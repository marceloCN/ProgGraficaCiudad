package com.graphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 5. Mejoras opcionales: trafico autonomo.
 * Hereda de Vehiculo para reutilizar fisica, dibujo y colisiones.
 * Puede usarse como clase ejecutable principal: la instancia principal
 * conserva el control del usuario, mientras gestiona 9 autos autonomos adicionales.
 */
public class VehiculoAutonomo extends Vehiculo {

    private static final Random RANDOM = new Random();

    // Color propio del vehiculo autonomo.
    private final float[] colorAuto = new float[3];

    // Modelo visual: 0=sedan, 1=camioneta, 2=deportivo.
    private final int modelo;

    // Punto hacia el que se dirige el auto.
    private float objetivoX;
    private float objetivoZ;

    // Celda anterior para evitar giros en U inmediatos.
    private int ultimaFila = -1;
    private int ultimaColumna = -1;

    // Temporizador para detectar atasco.
    private float tiempoAtascado = 0.0f;
    private float ultimaDistanciaRecorrida = 0.0f;

    // Reversa forzada al estar atascado por colision.
    private boolean enReversaForzada = false;
    private float tiempoReversaForzada = 0.0f;
    private float tiempoDetenidoColision = 0.0f;

    // Referencia a los demas vehiculos autonomos y al vehiculo del usuario.
    private final List<VehiculoAutonomo> otros;
    private final Vehiculo jugador;

    // Velocidad maxima propia (cada auto es ligeramente diferente).
    private final float velocidadMaxima;

    // true para la instancia principal ejecutable (controlada por el usuario).
    // false para los vehiculos autonomos gestionados por la instancia principal.
    private final boolean esJugador;

    // Constructor para la instancia principal (ejecutable).
    public VehiculoAutonomo() {
        this.esJugador = true;
        this.tieneChata = false;
        this.otros = new ArrayList<>();
        this.jugador = this;
        this.modelo = 0;
        this.velocidadMaxima = 16.0f;
        this.colorAuto[0] = 0.95f;
        this.colorAuto[1] = 0.24f;
        this.colorAuto[2] = 0.12f;
        this.objetivoX = autoX;
        this.objetivoZ = autoZ;
    }

    // Constructor para vehiculos autonomos gestionados.
    public VehiculoAutonomo(float x, float z, float anguloInicial, int modelo,
            List<VehiculoAutonomo> otros, Vehiculo jugador) {
        this.esJugador = false;
        this.tieneChata = false;
        this.autoX = x;
        this.autoZ = z;
        this.angulo = anguloInicial;
        this.velocidad = 0;
        this.modelo = modelo;
        this.otros = otros;
        this.jugador = jugador;
        this.velocidadMaxima = 5.0f + RANDOM.nextFloat() * 2.5f;

        // Color aleatorio pero visible.
        colorAuto[0] = 0.25f + RANDOM.nextFloat() * 0.75f;
        colorAuto[1] = 0.25f + RANDOM.nextFloat() * 0.75f;
        colorAuto[2] = 0.25f + RANDOM.nextFloat() * 0.75f;

        this.objetivoX = x;
        this.objetivoZ = z;
        elegirNuevoObjetivo();
    }

    // 5. Trafico autonomo: punto de entrada cuando esta clase es el ejecutable principal.
    public static void main(String[] args) {
        new VehiculoAutonomo().run();
    }

    @Override
    protected void actualizar(float deltaTime) {
        if (esJugador) {
            // La instancia principal usa el control de usuario heredado de Vehiculo.
            super.actualizar(deltaTime);
        } else {
            actualizarAutonomo(deltaTime);
        }
    }

    private void actualizarAutonomo(float deltaTime) {
        // 1. Si llego al objetivo, elegir uno nuevo.
        float dx = objetivoX - autoX;
        float dz = objetivoZ - autoZ;
        float distanciaObjetivo = (float) Math.sqrt(dx * dx + dz * dz);
        if (distanciaObjetivo < 2.5f) {
            elegirNuevoObjetivo();
            dx = objetivoX - autoX;
            dz = objetivoZ - autoZ;
            distanciaObjetivo = (float) Math.sqrt(dx * dx + dz * dz);
        }

        // Calcular angulo deseado: atan2(dx, dz) + PI para coincidir con la
        // convencion de Vehiculo (angulo 0 = -Z).
        float anguloDeseado = (float) Math.atan2(dx, dz) + (float) Math.PI;

        // Diferencia de angulo acotada a [-PI, PI].
        float diffAngulo = anguloDeseado - angulo;
        while (diffAngulo > Math.PI) diffAngulo -= 2.0f * Math.PI;
        while (diffAngulo < -Math.PI) diffAngulo += 2.0f * Math.PI;

        // 2. Velocidad objetivo base y frenado.
        float velocidadObjetivo = velocidadMaxima;

        // Respetar semaforo frente al vehiculo:
        // rojo/rojo+amarillo = detenerse; amarillo = reducir velocidad; verde = avanzar.
        int estadoSem = estadoSemaforoFrente(autoX, autoZ, angulo);
        if (estadoSem == 0 || estadoSem == 1) { // rojo o rojo+amarillo
            velocidadObjetivo = 0.0f;
        } else if (estadoSem == 3) { // amarillo: advertencia, reducir a 40%
            velocidadObjetivo = velocidadMaxima * 0.40f;
        }

        // Frenar si hay un coche justo delante o si esta en contacto con cualquier vehiculo.
        if (vehiculoDelante() || colisionConOtros(autoX, autoZ)) {
            velocidadObjetivo = 0.0f;
        }

        // Reducir velocidad en giros pronunciados para no cortar esquinas.
        float factorGiro = Math.max(0.35f, 1.0f - Math.abs(diffAngulo) / ((float) Math.PI * 0.8f));
        velocidadObjetivo *= factorGiro;

        // Aceleracion / frenado suave.
        if (velocidad < velocidadObjetivo) {
            velocidad += 4.0f * deltaTime;
        } else if (velocidad > velocidadObjetivo) {
            velocidad -= 6.0f * deltaTime;
        }
        velocidad = Math.max(-1.5f, Math.min(velocidadMaxima, velocidad));

        // 3. Girar suavemente hacia el objetivo.
        float velocidadGiro = 2.5f;
        if (Math.abs(velocidad) < 0.5f && Math.abs(diffAngulo) > 0.5f) {
            velocidadGiro = 4.0f;
        }
        if (Math.abs(diffAngulo) > 0.05f) {
            if (diffAngulo > 0) {
                angulo += velocidadGiro * deltaTime;
            } else {
                angulo -= velocidadGiro * deltaTime;
            }
        }

        // 4. Moverse solo si toda la trayectoria intermedia es valida (evita atravesar edificios u otros coches).
        float frenteX = -(float) Math.sin(angulo);
        float frenteZ = -(float) Math.cos(angulo);

        if (enReversaForzada) {
            float pasoRevX = -frenteX * 1.5f * deltaTime;
            float pasoRevZ = -frenteZ * 1.5f * deltaTime;
            boolean reversaLibre = true;
            for (int i = 1; i <= 4 && reversaLibre; i++) {
                float t = (float) i / 4;
                float cx = autoX + pasoRevX * t;
                float cz = autoZ + pasoRevZ * t;
                if (!puedeCircular(cx, cz) || colisionConOtros(cx, cz)) {
                    reversaLibre = false;
                }
            }
            if (reversaLibre) {
                autoX += pasoRevX;
                autoZ += pasoRevZ;
            }
            tiempoReversaForzada -= deltaTime;
            if (tiempoReversaForzada <= 0) {
                enReversaForzada = false;
                elegirNuevoObjetivo();
            }
            velocidad = 0;
        } else {
            float pasoX = frenteX * velocidad * deltaTime;
            float pasoZ = frenteZ * velocidad * deltaTime;
            boolean trayectoriaLibre = true;
            for (int i = 1; i <= 4 && trayectoriaLibre; i++) {
                float t = (float) i / 4;
                float cx = autoX + pasoX * t;
                float cz = autoZ + pasoZ * t;
                if (!puedeCircular(cx, cz) || colisionConOtros(cx, cz)) {
                    trayectoriaLibre = false;
                }
            }

            if (trayectoriaLibre) {
                autoX += pasoX;
                autoZ += pasoZ;
                int col = (int) Math.round((autoX + LIMITE) / CELDA - 0.5f);
                int fila = (int) Math.round((autoZ + LIMITE) / CELDA - 0.5f);
                ultimaColumna = col;
                ultimaFila = fila;
                tiempoDetenidoColision = 0.0f;
            } else {
                velocidad = 0;
                tiempoDetenidoColision += deltaTime;
                if (tiempoDetenidoColision > 1.5f) {
                    enReversaForzada = true;
                    tiempoReversaForzada = 0.6f;
                    tiempoDetenidoColision = 0.0f;
                }
                // Si es edificio/plaza, buscar otro objetivo.
                if (!puedeCircular(autoX + frenteX * 0.5f, autoZ + frenteZ * 0.5f)) {
                    elegirNuevoObjetivo();
                }
            }
        }

        // 5. Deteccion de atasco: si no avanza en 3 segundos, forzar nuevo objetivo o reversa.
        // No se permite girar 180° (la celda anterior sigue excluida).
        tiempoAtascado += deltaTime;
        float distRecorrida = Math.abs(velocidad) * deltaTime;
        ultimaDistanciaRecorrida += distRecorrida;
        if (tiempoAtascado > 3.0f) {
            if (ultimaDistanciaRecorrida < 1.5f) {
                velocidad = 0;
                if (!vehiculoDelante() && !colisionConOtros(autoX, autoZ)) {
                    elegirNuevoObjetivo();
                } else if (!enReversaForzada) {
                    enReversaForzada = true;
                    tiempoReversaForzada = 0.6f;
                }
            }
            tiempoAtascado = 0.0f;
            ultimaDistanciaRecorrida = 0.0f;
        }
    }

    // 5. Trafico autonomo: detecta colision con el jugador u otros autos autonomos.
    private boolean colisionConOtros(float x, float z) {
        if (jugador != null && jugador != this) {
            float dx = jugador.autoX - x;
            float dz = jugador.autoZ - z;
            if (dx * dx + dz * dz < DISTANCIA_COLISION_VEHICULO * DISTANCIA_COLISION_VEHICULO) {
                return true;
            }
            // 6. Chata: tambien se frena por la chata enganchada al jugador.
            if (jugador.tieneChata) {
                float cx = jugador.chataX - x;
                float cz = jugador.chataZ - z;
                if (cx * cx + cz * cz < DISTANCIA_COLISION_VEHICULO * DISTANCIA_COLISION_VEHICULO) {
                    return true;
                }
            }
        }
        if (otros != null) {
            for (VehiculoAutonomo otro : otros) {
                if (otro == this) continue;
                float dx = otro.autoX - x;
                float dz = otro.autoZ - z;
                if (dx * dx + dz * dz < DISTANCIA_COLISION_VEHICULO * DISTANCIA_COLISION_VEHICULO) {
                    return true;
                }
            }
        }
        return false;
    }

    // 5. Trafico autonomo: detecta si hay algun auto justo delante.
    private boolean vehiculoDelante() {
        float frenteX = -(float) Math.sin(angulo);
        float frenteZ = -(float) Math.cos(angulo);

        // Distancia de seguridad con el jugador (solo si esta delante).
        if (jugador != null && jugador != this) {
            float dx = jugador.autoX - autoX;
            float dz = jugador.autoZ - autoZ;
            float dot = dx * frenteX + dz * frenteZ;
            if (dot > 0.0f && dx * dx + dz * dz < DISTANCIA_COLISION_VEHICULO * DISTANCIA_COLISION_VEHICULO) {
                return true;
            }
            // 6. Chata: la chata del jugador tambien cuenta como obstaculo delante.
            if (jugador.tieneChata) {
                float cx = jugador.chataX - autoX;
                float cz = jugador.chataZ - autoZ;
                float dotChata = cx * frenteX + cz * frenteZ;
                if (dotChata > 0.0f
                        && cx * cx + cz * cz < DISTANCIA_COLISION_VEHICULO * DISTANCIA_COLISION_VEHICULO) {
                    return true;
                }
            }
        }
        // Distancia de seguridad con otros autos autonomos (solo si estan delante).
        if (otros != null) {
            for (VehiculoAutonomo otro : otros) {
                if (otro == this) continue;
                float dx = otro.autoX - autoX;
                float dz = otro.autoZ - autoZ;
                float dot = dx * frenteX + dz * frenteZ;
                if (dot > 0.0f && dx * dx + dz * dz < DISTANCIA_COLISION_VEHICULO * DISTANCIA_COLISION_VEHICULO) {
                    return true;
                }
            }
        }
        return false;
    }

    // 5. Trafico autonomo: elige un nuevo punto objetivo en una calle cercana.
    private void elegirNuevoObjetivo() {
        // Calcular celda actual aproximada.
        int colActual = (int) Math.round((autoX + LIMITE) / CELDA - 0.5f);
        int filaActual = (int) Math.round((autoZ + LIMITE) / CELDA - 0.5f);
        colActual = Math.max(0, Math.min(MAPA.length - 1, colActual));
        filaActual = Math.max(0, Math.min(MAPA.length - 1, filaActual));

        // Si la celda calculada no es calle, buscar la celda de calle mas cercana.
        if (MAPA[filaActual][colActual] != 0) {
            float menorDist = Float.MAX_VALUE;
            for (int f = 0; f < MAPA.length; f++) {
                for (int c = 0; c < MAPA[f].length; c++) {
                    if (MAPA[f][c] == 0) {
                        float dx = centro(c) - autoX;
                        float dz = centro(f) - autoZ;
                        float dist = dx * dx + dz * dz;
                        if (dist < menorDist) {
                            menorDist = dist;
                            filaActual = f;
                            colActual = c;
                        }
                    }
                }
            }
        }

        // Recopilar celdas de calle adyacentes validas.
        List<int[]> candidatas = new ArrayList<>();
        int[][] deltas = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } };
        for (int[] d : deltas) {
            int nc = colActual + d[0];
            int nf = filaActual + d[1];
            if (nc >= 0 && nc < MAPA.length && nf >= 0 && nf < MAPA.length && MAPA[nf][nc] == 0) {
                candidatas.add(new int[] { nc, nf });
            }
        }

        // Eliminar la celda anterior para evitar giros en U inmediatos.
        if (ultimaFila >= 0 && ultimaColumna >= 0) {
            candidatas.removeIf(c -> c[0] == ultimaColumna && c[1] == ultimaFila);
        }

        // 5. Trafico autonomo: descartar objetivos que obliguen a un giro en U de 180 grados.
        // Solo se aceptan celdas al frente o a los lados (producto punto positivo con el frente).
        if (!candidatas.isEmpty()) {
            float frenteX = -(float) Math.sin(angulo);
            float frenteZ = -(float) Math.cos(angulo);
            List<int[]> alFrente = new ArrayList<>();
            for (int[] c : candidatas) {
                float dx = centro(c[0]) - autoX;
                float dz = centro(c[1]) - autoZ;
                if (dx * frenteX + dz * frenteZ > 0.0f) {
                    alFrente.add(c);
                }
            }
            if (alFrente.isEmpty()) {
                // Sin opciones al frente: conservar la menos mala (la de mayor producto punto).
                float mejorDot = -Float.MAX_VALUE;
                int[] mejor = candidatas.get(0);
                for (int[] c : candidatas) {
                    float dx = centro(c[0]) - autoX;
                    float dz = centro(c[1]) - autoZ;
                    float dot = dx * frenteX + dz * frenteZ;
                    if (dot > mejorDot) {
                        mejorDot = dot;
                        mejor = c;
                    }
                }
                alFrente.add(mejor);
            }
            candidatas = alFrente;
        }

        if (candidatas.isEmpty()) {
            // Caso extremo: quedo atrapado; permitir volver a la anterior.
            if (ultimaFila >= 0 && ultimaColumna >= 0) {
                objetivoX = centro(ultimaColumna);
                objetivoZ = centro(ultimaFila);
            }
            return;
        }

        // Elegir aleatoriamente.
        int[] elegida = candidatas.get(RANDOM.nextInt(candidatas.size()));
        objetivoX = centro(elegida[0]);
        objetivoZ = centro(elegida[1]);
    }

    // 5. Trafico autonomo: dibuja el auto segun su modelo y color.
    @Override
    protected void dibujarAuto() {
        switch (modelo) {
            case 1 -> dibujarCamioneta();
            case 2 -> dibujarDeportivo();
            default -> dibujarSedan();
        }
    }

    // Modelo 0: sedan (similar al jugador pero con color variable).
    private void dibujarSedan() {
        pieza(0, 0.65f, 0, 1.65f, 0.55f, 2.6f, colorAuto[0], colorAuto[1], colorAuto[2]);
        pieza(0, 1.12f, 0.12f, 1.3f, 0.55f, 1.2f, 0.22f, 0.65f, 0.78f);
        float[] ladosRueda = { -0.88f, 0.88f };
        float[] ejesRueda = { -0.82f, 0.82f };
        for (float x : ladosRueda) {
            for (float z : ejesRueda) {
                pieza(x, 0.38f, z, 0.24f, 0.58f, 0.6f, 0.055f, 0.65f, 0.08f);
            }
        }
        dibujarLuces();
    }

    // Modelo 1: camioneta (mas alta y larga).
    private void dibujarCamioneta() {
        pieza(0, 0.80f, 0, 1.85f, 0.75f, 3.2f, colorAuto[0], colorAuto[1], colorAuto[2]);
        pieza(0, 1.40f, 0.25f, 1.6f, 0.70f, 1.6f, 0.25f, 0.60f, 0.75f);
        float[] ladosRueda = { -1.0f, 1.0f };
        float[] ejesRueda = { -1.1f, 1.1f };
        for (float x : ladosRueda) {
            for (float z : ejesRueda) {
                pieza(x, 0.45f, z, 0.30f, 0.65f, 0.70f, 0.07f, 0.07f, 0.07f);
            }
        }
        dibujarLuces();
    }

    // Modelo 2: deportivo (bajo y ancho).
    private void dibujarDeportivo() {
        pieza(0, 0.55f, 0, 1.95f, 0.45f, 2.4f, colorAuto[0], colorAuto[1], colorAuto[2]);
        pieza(0, 0.95f, 0.05f, 1.5f, 0.40f, 1.1f, 0.20f, 0.20f, 0.25f);
        float[] ladosRueda = { -1.0f, 1.0f };
        float[] ejesRueda = { -0.95f, 0.95f };
        for (float x : ladosRueda) {
            for (float z : ejesRueda) {
                pieza(x, 0.32f, z, 0.28f, 0.45f, 0.55f, 0.08f, 0.08f, 0.08f);
            }
        }
        dibujarLuces();
    }

    private void dibujarLuces() {
        float[] ladosFaros = { -0.55f, 0.55f };
        emision(0.5f);
        for (float x : ladosFaros) {
            pieza(x, 0.68f, -1.32f, 0.38f, 0.2f, 0.07f, 1, 0.95f, 0.65f);
        }
        emision(0.0f);
        for (float x : ladosFaros) {
            pieza(x, 0.68f, 1.32f, 0.35f, 0.2f, 0.07f, 1, 0.05f, 0.05f);
        }
    }

    // 5. Trafico autonomo: la instancia principal controla camara, titulo y faros;
    // las instancias autonomas no.
    @Override
    protected void configurarCamara() {
        if (esJugador) {
            super.configurarCamara();
        }
    }

    @Override
    protected void actualizarTitulo() {
        if (esJugador) {
            super.actualizarTitulo();
        }
    }

    @Override
    protected void enviarFarosVehiculo() {
        if (esJugador) {
            super.enviarFarosVehiculo();
        }
    }

    // 5. Trafico autonomo: expone la posicion y angulo para que Vehiculo envie sus faros.
    public float getAutoX() { return autoX; }
    public float getAutoZ() { return autoZ; }
    public float getAngulo() { return angulo; }
}
