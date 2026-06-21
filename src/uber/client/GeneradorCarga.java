package uber.client;

import uber.shared.*;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class GeneradorCarga {

    private static final String IP_SERVIDOR = "127.0.0.1";
    private static final int[] PUERTOS = {5000, 5001, 5002};
    private static final int INTERVALO_REPORTE_MS = 5000;

    private static final ConcurrentLinkedQueue<Long> todasLatencias = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger totalEnviados = new AtomicInteger(0);
    private static final AtomicInteger totalExitosos = new AtomicInteger(0);
    private static final AtomicInteger totalErroresNegocio = new AtomicInteger(0);
    private static final AtomicInteger totalErroresRed = new AtomicInteger(0);

    private static final AtomicInteger ventanaEnviados = new AtomicInteger(0);
    private static final AtomicInteger ventanaExitosos = new AtomicInteger(0);
    private static final AtomicInteger ventanaErroresNegocio = new AtomicInteger(0);
    private static final AtomicInteger ventanaErroresRed = new AtomicInteger(0);
    private static final ConcurrentLinkedQueue<Long> ventanaLatencias = new ConcurrentLinkedQueue<>();

    private static volatile boolean ejecutando = true;

    public static void main(String[] args) {
        int numClientes = 50;
        int duracionSeg = 70;

        if (args.length >= 1) numClientes = Integer.parseInt(args[0]);
        if (args.length >= 2) duracionSeg = Integer.parseInt(args[1]);

        System.out.println("================================================");
        System.out.println("  GENERADOR DE CARGA - UBER DISTRIBUIDO");
        System.out.println("================================================");
        System.out.println("  Clientes concurrentes : " + numClientes);
        System.out.println("  Duracion              : " + duracionSeg + " segundos");
        System.out.println("  Nodos destino         : " + Arrays.toString(PUERTOS));
        System.out.println("================================================");
        System.out.println();
        System.out.println("  PRUEBA DE FALLO INDUCIDO (seccion 3.3):");
        System.out.println("  A los ~30s, mata el proceso del lider");
        System.out.println("  (NODO_3 / puerto 5002) con Ctrl+C en su");
        System.out.println("  terminal. Observa el pico de err_red y");
        System.out.println("  la recuperacion en las ventanas siguientes.");
        System.out.println();

        ExecutorService pool = Executors.newFixedThreadPool(numClientes + 1);
        long inicio = System.currentTimeMillis();

        final int clientesFinal = numClientes;

        pool.submit(() -> hiloReportador(inicio));

        for (int i = 0; i < numClientes; i++) {
            final String userId = "Carga_" + i;
            final int idx = i;
            pool.submit(() -> ejecutarCliente(userId, idx));
        }

        try { Thread.sleep(duracionSeg * 1000L); } catch (InterruptedException ignored) {}
        ejecutando = false;

        pool.shutdown();
        try { pool.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}

        long duracionReal = (System.currentTimeMillis() - inicio) / 1000;
        int mensajesCoord = consultarMetricasServidor();

        imprimirReporte(clientesFinal, duracionReal, mensajesCoord);
    }

    private static void hiloReportador(long inicio) {
        int ventanaNum = 0;
        while (ejecutando) {
            try { Thread.sleep(INTERVALO_REPORTE_MS); } catch (InterruptedException e) { return; }
            ventanaNum++;

            int env = ventanaEnviados.getAndSet(0);
            int ok = ventanaExitosos.getAndSet(0);
            int negocio = ventanaErroresNegocio.getAndSet(0);
            int red = ventanaErroresRed.getAndSet(0);

            List<Long> lats = drenarCola(ventanaLatencias);
            double avgLat = lats.stream().mapToLong(x -> x).average().orElse(0);
            long p95 = percentil(lats, 95);
            double throughput = env / (INTERVALO_REPORTE_MS / 1000.0);
            long elapsed = (System.currentTimeMillis() - inicio) / 1000;

            System.out.printf("[t=%3ds] V%2d | env=%4d ok=%4d err_neg=%3d err_red=%3d | %.1f req/s | avg=%.0fms p95=%dms%n",
                    elapsed, ventanaNum, env, ok, negocio, red, throughput, avgLat, p95);
        }
    }

    private static void ejecutarCliente(String userId, int idx) {
        int ciclo = 0;

        while (ejecutando) {
            int puerto = PUERTOS[(idx + ciclo) % PUERTOS.length];
            enviarPeticion(userId, TipoMensaje.SOLICITAR_VIAJE,
                    new SolicitudViaje("Origen_" + idx, "Destino_" + idx, false, null), puerto);
            pausaCorta();
            if (!ejecutando) break;

            int puerto2 = PUERTOS[(idx + ciclo + 1) % PUERTOS.length];
            enviarPeticion(userId, TipoMensaje.CONSULTAR_VIAJES, null, puerto2);
            pausaCorta();
            if (!ejecutando) break;

            int puerto3 = PUERTOS[(idx + ciclo + 2) % PUERTOS.length];
            enviarPeticion(userId, TipoMensaje.FINALIZAR_VIAJE, null, puerto3);
            pausaCorta();

            ciclo++;
        }
    }

    private static void enviarPeticion(String userId, TipoMensaje tipo, Object payload, int puerto) {
        if (!ejecutando) return;

        String requestId = UUID.randomUUID().toString();
        MensajeUber peticion = new MensajeUber(tipo, userId, payload, requestId, 0);

        totalEnviados.incrementAndGet();
        ventanaEnviados.incrementAndGet();

        long t0 = System.currentTimeMillis();

        try (
                Socket socket = new Socket(IP_SERVIDOR, puerto);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            socket.setSoTimeout(5000);

            out.writeObject(peticion);
            out.flush();

            MensajeUber ack = (MensajeUber) in.readObject();
            MensajeUber respuesta = (MensajeUber) in.readObject();

            long latencia = System.currentTimeMillis() - t0;
            registrarLatencia(latencia);

            if (respuesta.getAccion() == TipoMensaje.ERROR) {
                totalErroresNegocio.incrementAndGet();
                ventanaErroresNegocio.incrementAndGet();
            } else {
                totalExitosos.incrementAndGet();
                ventanaExitosos.incrementAndGet();
            }

        } catch (Exception e) {
            long latencia = System.currentTimeMillis() - t0;
            registrarLatencia(latencia);
            totalErroresRed.incrementAndGet();
            ventanaErroresRed.incrementAndGet();
        }
    }

    private static void registrarLatencia(long latencia) {
        todasLatencias.add(latencia);
        ventanaLatencias.add(latencia);
    }

    private static void pausaCorta() {
        try {
            Thread.sleep(50 + ThreadLocalRandom.current().nextInt(150));
        } catch (InterruptedException ignored) {}
    }

    private static int consultarMetricasServidor() {
        for (int puerto : PUERTOS) {
            try (
                    Socket socket = new Socket(IP_SERVIDOR, puerto);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
            ) {
                socket.setSoTimeout(3000);
                String reqId = UUID.randomUUID().toString();
                out.writeObject(new MensajeUber(TipoMensaje.CONSULTAR_METRICAS, "CARGA_TEST", null, reqId, 0));
                out.flush();

                in.readObject();
                MensajeUber respuesta = (MensajeUber) in.readObject();

                if (respuesta.getPayload() instanceof Integer) {
                    return (Integer) respuesta.getPayload();
                }
                return -1;
            } catch (Exception e) {
                // nodo caido, intentar siguiente
            }
        }
        return -1;
    }

    private static void imprimirReporte(int numClientes, long duracionSeg, int mensajesCoord) {
        List<Long> lats = new ArrayList<>(todasLatencias);
        Collections.sort(lats);

        int total = totalEnviados.get();
        int ok = totalExitosos.get();
        int errNeg = totalErroresNegocio.get();
        int errRed = totalErroresRed.get();
        double throughput = duracionSeg > 0 ? (double) total / duracionSeg : 0;
        double avgLat = lats.stream().mapToLong(x -> x).average().orElse(0);
        long minLat = lats.isEmpty() ? 0 : lats.get(0);
        long p50 = percentil(lats, 50);
        long p95 = percentil(lats, 95);
        long p99 = percentil(lats, 99);
        long maxLat = lats.isEmpty() ? 0 : lats.get(lats.size() - 1);
        double errorRate = total > 0 ? (double) (errNeg + errRed) / total * 100 : 0;
        double errorRedRate = total > 0 ? (double) errRed / total * 100 : 0;

        System.out.println();
        System.out.println("================================================");
        System.out.println("       REPORTE FINAL - PRUEBA DE CARGA");
        System.out.println("================================================");
        System.out.println();
        System.out.println("--- Configuracion ---");
        System.out.println("  Clientes concurrentes : " + numClientes);
        System.out.println("  Duracion real         : " + duracionSeg + " segundos");
        System.out.println();
        System.out.println("--- Volumen ---");
        System.out.println("  Total peticiones      : " + total);
        System.out.println("  Exitosas              : " + ok);
        System.out.println("  Errores de negocio    : " + errNeg);
        System.out.println("  Errores de red/sistema: " + errRed);
        System.out.printf("  Tasa error total      : %.2f%%%n", errorRate);
        System.out.printf("  Tasa error red        : %.2f%%%n", errorRedRate);
        System.out.println();
        System.out.println("--- Rendimiento ---");
        System.out.printf("  Throughput            : %.2f req/s%n", throughput);
        System.out.println();
        System.out.println("--- Latencia (ms) ---");
        System.out.println("  Minima                : " + minLat + " ms");
        System.out.printf("  Promedio              : %.2f ms%n", avgLat);
        System.out.println("  Mediana (p50)         : " + p50 + " ms");
        System.out.println("  Percentil 95          : " + p95 + " ms");
        System.out.println("  Percentil 99          : " + p99 + " ms");
        System.out.println("  Maxima                : " + maxLat + " ms");
        System.out.println();
        System.out.println("--- Coordinacion ---");
        if (mensajesCoord >= 0) {
            System.out.println("  Mensajes coordinacion : " + mensajesCoord);
        } else {
            System.out.println("  Mensajes coordinacion : (no disponible)");
        }
        System.out.println();
        System.out.println("================================================");
        System.out.println();
        System.out.println("NOTA: 'Errores de negocio' = restricciones del");
        System.out.println("dominio (viaje activo, sin conductores, etc.).");
        System.out.println("'Errores de red' = nodo caido, timeout, conexion");
        System.out.println("rechazada. Para la seccion 3.3, observar el pico");
        System.out.println("de err_red en las ventanas tras matar al lider.");
    }

    private static List<Long> drenarCola(ConcurrentLinkedQueue<Long> cola) {
        List<Long> resultado = new ArrayList<>();
        Long val;
        while ((val = cola.poll()) != null) resultado.add(val);
        return resultado;
    }

    private static long percentil(List<Long> datos, int p) {
        if (datos.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(datos);
        Collections.sort(sorted);
        int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }
}
