package com.graphics;

import java.util.HashMap;
import java.util.Map;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_NO_ERROR;
import static org.lwjgl.opengl.GL11.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glScissor;
import static org.lwjgl.opengl.GL11.glClear;
import static org.lwjgl.opengl.GL11.glClearColor;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGetError;
import static org.lwjgl.opengl.GL11.glViewport;
import static org.lwjgl.opengl.GL15.glDeleteBuffers;
import static org.lwjgl.opengl.GL20.glDeleteProgram;
import static org.lwjgl.opengl.GL20.glGetUniformLocation;
import static org.lwjgl.opengl.GL20.glUniform1f;
import static org.lwjgl.opengl.GL20.glUniform1i;
import static org.lwjgl.opengl.GL20.glUniform3f;
import static org.lwjgl.opengl.GL20.glUseProgram;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL33.*;

public class AppCiudad {
    protected long ventana;
    // Se hacen protected para que VehiculoAutonomo pueda compartir los recursos OpenGL.
    protected int programa;
    protected int vao;
    private int vbo;
    protected int ancho = 1100;
    protected int alto = 760;

    // Angulo inicial de la camara de la ciudad
    protected float orbita = 0.6f;

    // Cambiar el angulo o la vista del mapa
    private boolean vistaMapa = false;

    // 3. Minimapa: visible por defecto, se alterna con la tecla M.
    protected boolean mostrarMinimapa = true;

    // Distancia del origen a cada borde.
    // PUNTO 1: se aumenta de 35 a 55 porque el mapa pasa de 7x7 a 11x11 celdas
    // (11 celdas * CELDA 10 = 110 unidades de ancho, la mitad es 55).
    protected static final float LIMITE = 55;

    // Ancho y profundidad de cada celda del mapa
    protected static final float CELDA = 10;

    // Se hace protected para que VehiculoAutonomo reutilice las ubicaciones de uniforms.
    protected Map<String, Integer> uniforms = new HashMap<>();

    // 2. Iluminacion: modo diurno (false) o nocturno (true), alternable con N.
    protected boolean modoNocturno = false;

    // 2. Iluminacion: posiciones de las farolas (X, Y, Z).
    // Se generan en la esquina inferior-izquierda de cada edificio, mirando a las calles.
    // Se rellena dinamicamente en generarFarolas() a partir del MAPA.
    protected float[][] farolas;

    // 5. Trafico autonomo: posiciones de los semaforos (esquina noreste de edificios).
    // Se rellena en generarSemaforos(); necesario para que los autos autonomos respeten las luces.
    protected float[][] semaforos;

    // PUNTO 1: mapa ampliado a 11x11.
    // 0 = calle, 1 = edificio, 2 = parque.
    // Las filas/columnas pares (0,2,4...) son calles; las impares son manzanas.
    // Contiene mas de 12 edificios y mas de 4 parques distribuidos de forma variada.
    protected static final int[][] MAPA = {
            { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 1, 0, 1, 0, 1, 0, 2, 0, 3, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 1, 0, 2, 0, 1, 0, 1, 0, 2, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 1, 0, 1, 0, 2, 0, 1, 0, 1, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 2, 0, 1, 0, 1, 0, 1, 0, 1, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            { 0, 1, 0, 1, 0, 1, 0, 2, 0, 1, 0 },
            { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }
    };

    public static void main(String[] args) {
        new AppCiudad().run();
    }

    // Organizar las tres fases de la aplicación y limpiar en caso ocurra un error
    public void run() {
        // Preparamos mensajes de error en la consola
        GLFWErrorCallback errores = GLFWErrorCallback.createPrint(System.err);
        // Instamos el manejador de errores en GLFW
        errores.set();
        try {
            iniciar();
            loop();
        } finally {
            limpiar();
            glfwSetErrorCallback(null);
            errores.free();
        }
    }

    // Configurar GLFW y OpenGL igual que las clases de App
    protected void iniciar() {
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("No se pudo inicializar GLFW");
        }

        // Reestablece todas las opciones por defecto
        GLFW.glfwDefaultWindowHints();

        // Ocultar la ventana al principio; la mostrar recien cuando OpenGL este listo
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        // Permitir que el usuario pueda redimensionar la pantalla
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_TRUE);
        // Queremos que use la version de OpenGL 3.3
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        // Solo funciones modernas de OpenGL (evitar compatibilidad con codigo muy
        // antiguo)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        // MacOS hay que reactivar para usar el core profile
        //GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);

        // Creamos la ventana: ancho, alto, tiitulo, monitor(0 = ventana normal),
        // ventana para compartir
        ventana = GLFW.glfwCreateWindow(ancho, alto, "Ciudad | Flechas: orbitar | Esc: Salir", 0, 0);

        // Decir que todas las ordenes de OPENGL que ejecutamos a partir de ahora
        // se aplique a esta venta
        GLFW.glfwMakeContextCurrent(ventana);

        // Sincronizar el dibujo con el monitor (1 = esperar al siguiente refresco)
        // Evitamos el "tearing" (imagen partida)
        GLFW.glfwSwapInterval(1);
        // hacer visible la ventana
        GLFW.glfwShowWindow(ventana);

        // Carga en java las funciones de OpenGl
        // LWJGL necesita este paso para enlazar las llamadas de Java con la GPU
        GL.createCapabilities();

        // Hace que las superficie cercana oculten a las lejanas
        glEnable(GL_DEPTH_TEST);

        // Registramos la funcion del evento del teclado para cerrar la ventana
        glfwSetKeyCallback(ventana, (ventanaEvento, key, scancode, action, mods) -> {
            if (action == GLFW_PRESS) {
                tecla(key);
            }
        });

        crearPrograma();
        crearCubo();
        // 2. Iluminacion: generar farolas en esquinas de edificios segun el MAPA actual.
        generarFarolas();
        // 5. Trafico autonomo: generar lista de semaforos para consulta de estado.
        generarSemaforos();
    }

    // 2. Iluminacion: coloca una farola en la esquina inferior-izquierda de cada edificio.
    // La esquina (-x, -z) de cada manzana da a dos calles adyacentes.
    protected void generarFarolas() {
        java.util.List<float[]> lista = new java.util.ArrayList<>();
        for (int fila = 0; fila < MAPA.length; fila++) {
            for (int col = 0; col < MAPA[fila].length; col++) {
                if (MAPA[fila][col] == 1) { // edificio
                    float cx = centro(col);
                    float cz = centro(fila);
                    // Esquina de la casa (edificio de 7x7) ligeramente hacia la acera/sidewalk.
                    lista.add(new float[] { cx - 3.8f, 5.0f, cz - 3.8f });
                }
            }
        }
        farolas = lista.toArray(new float[0][]);
    }

    // 5. Trafico autonomo: guarda la posicion de cada semaforo (esquina noreste de edificios).
    protected void generarSemaforos() {
        java.util.List<float[]> lista = new java.util.ArrayList<>();
        for (int fila = 0; fila < MAPA.length; fila++) {
            for (int col = 0; col < MAPA[fila].length; col++) {
                if (MAPA[fila][col] == 1) { // edificio
                    float cx = centro(col);
                    float cz = centro(fila);
                    lista.add(new float[] { cx + 3.8f, 5.0f, cz + 3.8f });
                }
            }
        }
        semaforos = lista.toArray(new float[0][]);
    }

    // 5. Trafico autonomo: devuelve el estado actual de un semaforo por su indice.
    // 0=rojo, 1=rojo+amarillo, 2=verde, 3=amarillo.
    protected int estadoSemaforo(int indice) {
        double tiempo = glfwGetTime();
        float fase = (float) ((tiempo + indice * 0.7) % 8.0);
        if (fase < 3.0f) return 0;
        if (fase < 4.0f) return 1;
        if (fase < 7.0f) return 2;
        return 3;
    }

    // 5. Trafico autonomo: indica si hay un semaforo en ROJO frente al vehiculo
    // (dentro de un cono frontal de ~60 grados y a menos de 12 unidades).
    protected boolean semaforoEnRojoFrente(float x, float z, float angulo) {
        return estadoSemaforoFrente(x, z, angulo) == 0 || estadoSemaforoFrente(x, z, angulo) == 1;
    }

    // 5. Trafico autonomo: devuelve el estado del semaforo relevante frente al vehiculo.
    // -1 = no hay semaforo cercano frente al vehiculo.
    // 0 = rojo, 1 = rojo+amarillo, 2 = verde, 3 = amarillo.
    protected int estadoSemaforoFrente(float x, float z, float angulo) {
        if (semaforos == null || semaforos.length == 0) {
            return -1;
        }
        float frenteX = -(float) Math.sin(angulo);
        float frenteZ = -(float) Math.cos(angulo);

        int mejor = -1;
        float menorDot = Float.MAX_VALUE;
        for (int i = 0; i < semaforos.length; i++) {
            float dx = semaforos[i][0] - x;
            float dz = semaforos[i][2] - z;
            float distSq = dx * dx + dz * dz;
            // Solo semaforos cercanos (dentro de 12 unidades).
            if (distSq > 12.0f * 12.0f) {
                continue;
            }
            // Solo semaforos que esten frente al vehiculo (producto punto positivo).
            float dot = dx * frenteX + dz * frenteZ;
            if (dot <= 0.0f) {
                continue;
            }
            // Aproximadamente dentro de un cono de 60 grados (cos 30 = 0.866).
            float cosAngulo = dot / (float) Math.sqrt(distSq);
            if (cosAngulo < 0.707f) {
                continue;
            }
            // Elegir el semaforo mas alineado con la direccion del vehiculo (menor distancia angular).
            if (dot < menorDot) {
                menorDot = dot;
                mejor = i;
            }
        }
        if (mejor < 0) {
            return -1;
        }
        return estadoSemaforo(mejor);
    }

    private void crearPrograma() {
        String vertexSrc = vertexShader().replaceAll("(?m)^\\s+", "");
        // Algunos drivers no aceptan comentarios en la misma linea que #version
        vertexSrc = vertexSrc.replaceFirst("^(#version\\s+\\d+\\s+\\w+).*", "$1");
        int vertex = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vertex, vertexSrc);
        glCompileShader(vertex);
        if (glGetShaderi(vertex, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Error en vertex shader:\n" + glGetShaderInfoLog(vertex));
        }

        String fragmentSrc = fragmentShader().replaceAll("(?m)^\\s+", "");
        int fragment = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fragment, fragmentSrc);
        glCompileShader(fragment);
        if (glGetShaderi(fragment, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Error en fragment shader:\n" + glGetShaderInfoLog(fragment));
        }

        programa = glCreateProgram();
        glAttachShader(programa, vertex);
        glAttachShader(programa, fragment);
        glLinkProgram(programa);
        if (glGetProgrami(programa, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("Error al enlazar programa:\n" + glGetProgramInfoLog(programa));
        }

        glDeleteShader(vertex);
        glDeleteShader(fragment);
    }

    private void crearCubo() {
        float[] vertices = {
            // Cara frontal (+Z)
            -0.5f, -0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
             0.5f, -0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
             0.5f,  0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
            -0.5f, -0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
             0.5f,  0.5f,  0.5f,  0.0f, 0.0f, 1.0f,
            -0.5f,  0.5f,  0.5f,  0.0f, 0.0f, 1.0f,

            // Cara trasera (-Z)
             0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
            -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
            -0.5f,  0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
             0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
            -0.5f,  0.5f, -0.5f,  0.0f, 0.0f, -1.0f,
             0.5f,  0.5f, -0.5f,  0.0f, 0.0f, -1.0f,

            // Cara derecha (+X)
             0.5f, -0.5f,  0.5f,  1.0f, 0.0f, 0.0f,
             0.5f, -0.5f, -0.5f,  1.0f, 0.0f, 0.0f,
             0.5f,  0.5f, -0.5f,  1.0f, 0.0f, 0.0f,
             0.5f, -0.5f,  0.5f,  1.0f, 0.0f, 0.0f,
             0.5f,  0.5f, -0.5f,  1.0f, 0.0f, 0.0f,
             0.5f,  0.5f,  0.5f,  1.0f, 0.0f, 0.0f,

            // Cara izquierda (-X)
            -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f,
            -0.5f, -0.5f,  0.5f, -1.0f, 0.0f, 0.0f,
            -0.5f,  0.5f,  0.5f, -1.0f, 0.0f, 0.0f,
            -0.5f, -0.5f, -0.5f, -1.0f, 0.0f, 0.0f,
            -0.5f,  0.5f,  0.5f, -1.0f, 0.0f, 0.0f,
            -0.5f,  0.5f, -0.5f, -1.0f, 0.0f, 0.0f,

            // Cara superior (+Y)
            -0.5f,  0.5f,  0.5f,  0.0f, 1.0f, 0.0f,
             0.5f,  0.5f,  0.5f,  0.0f, 1.0f, 0.0f,
             0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f,
            -0.5f,  0.5f,  0.5f,  0.0f, 1.0f, 0.0f,
             0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f,
            -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, 0.0f,

            // Cara inferior (-Y)
            -0.5f, -0.5f, -0.5f,  0.0f, -1.0f, 0.0f,
             0.5f, -0.5f, -0.5f,  0.0f, -1.0f, 0.0f,
             0.5f, -0.5f,  0.5f,  0.0f, -1.0f, 0.0f,
            -0.5f, -0.5f, -0.5f,  0.0f, -1.0f, 0.0f,
             0.5f, -0.5f,  0.5f,  0.0f, -1.0f, 0.0f,
            -0.5f, -0.5f,  0.5f,  0.0f, -1.0f, 0.0f
        };

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);

        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 6 * Float.BYTES, 0);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, 6 * Float.BYTES, 3L * Float.BYTES);

        glBindVertexArray(0);
    }

    private void loop() {
        // Guardamos el instante inicial para calcular el tiempo entre cuadros
        double tiempoAnterior = glfwGetTime();
        // Cuenta los cuadros dibujados para la comprobación
        int cuadros = 0;
        // Cero permite jugar; otro valor limita el arranque de prueba
        int maxCuadros = Integer.getInteger("demo.frames", 0);
        // Reserva espacio para que GLFW escriba el ancho en pixeles
        int[] anchoReal = new int[1];
        // Reserva espacio para qie GLFW escriba el alto en pixeles
        int[] altoReal = new int[1];

        while (!glfwWindowShouldClose(ventana)) {
            // Procesa el teclado, redimensionamiento y boton de cierre
            glfwPollEvents();
            // Consulta el tiempo actual en segundos
            double tiempoActual = glfwGetTime();
            float deltaTime = (float) Math.min(tiempoActual - tiempoAnterior, 0.05);
            tiempoAnterior = tiempoActual;
            actualizar(deltaTime);
            glfwGetFramebufferSize(ventana, anchoReal, altoReal);
            ancho = anchoReal[0];
            alto = altoReal[0];
            if (ancho > 0 && alto > 0) {
                dibujarFrame();
            }
            glfwSwapBuffers(ventana);
            cuadros++;
            if (maxCuadros > 0 && cuadros >= maxCuadros) {
                glfwSetWindowShouldClose(ventana, true);
            }
        }
    }

    private void limpiar() {
        if (programa != 0) {
            glDeleteProgram(programa);
        }
        if (vbo != 0) {
            glDeleteBuffers(vbo);
        }
        if (vao != 0) {
            glDeleteVertexArrays(vao);
        }
        if (ventana != 0) {
            Callbacks.glfwFreeCallbacks(ventana);
            glfwDestroyWindow(ventana);
        }
        glfwTerminate();
    }

    // ===================== Teclas y camaras ===================

    /** Atiende las acciones de una sola pulsación */
    protected void tecla(int key) {
        if (key == GLFW_KEY_ESCAPE) {
            glfwSetWindowShouldClose(ventana, true);
        }
        // 2. Iluminacion: tecla N alterna entre dia y noche.
        if (key == GLFW_KEY_N) {
            modoNocturno = !modoNocturno;
        }
        // 3. Minimapa: tecla M alterna la visibilidad del minimapa.
        if (key == GLFW_KEY_M) {
            mostrarMinimapa = !mostrarMinimapa;
        }
    }

    protected boolean pulsada(int key) {
        return glfwGetKey(ventana, key) == GLFW_PRESS;
    }

    /** Mueve la camara alrededor de la ciudad con las flechas */
    protected void actualizar(float deltaTime) {
        if (pulsada(GLFW_KEY_LEFT)) {
            orbita -= deltaTime; // Reducimos el angulo
        }

        if (pulsada(GLFW_KEY_RIGHT)) {
            orbita += deltaTime; // Aumentamos el angulo
        }
    }

    protected void configurarCamara() {
        // PUNTO 1: la camara se aleja para abarcar toda la ciudad ampliada.
        // Antes usaba 65; ahora 100 para cubrir el nuevo limite de 55.
        float camaraX = (float) Math.sin(orbita) * 100;
        float camaraZ = (float) Math.cos(orbita) * 100;

        vector("uOjo", camaraX, 80, camaraZ);
        vector("uObjetivo", 0, 0, 0);
        decimal("uAspecto", (float) ancho / alto);
    }

    private void prepararLuces() {
        // 2. Iluminacion: ambiente diurno y nocturno.
        // La luz ambiental evita que las caras en sombra se vean completamente negras.
        // La luz direccional simula el sol (dia) o la luna (noche).
        if (modoNocturno) {
            vector("uAmbiente", 0.08f, 0.08f, 0.12f);
            vector("uDireccionalDir", 0.20f, -0.80f, 0.10f);
            vector("uDireccionalColor", 0.10f, 0.10f, 0.15f);
        } else {
            vector("uAmbiente", 0.45f, 0.45f, 0.50f);
            vector("uDireccionalDir", 0.20f, -0.70f, -0.20f);
            vector("uDireccionalColor", 0.85f, 0.80f, 0.70f);
        }
        // 3. Decoracion urbana: indicador de noche para ventanas iluminadas.
        entero("uModoNocturno", modoNocturno ? 1 : 0);

        // 2. Iluminacion: farolas (luces puntuales con atenuacion por distancia).
        entero("uNumFarolas", farolas.length);
        for (int i = 0; i < farolas.length; i++) {
            vector("uFarolasPos[" + i + "]", farolas[i][0], farolas[i][1], farolas[i][2]);
            // Color calido e intenso para que se note la iluminacion nocturna.
            vector("uFarolasColor[" + i + "]", 1.10f, 0.85f, 0.40f);
        }

        // 2. Iluminacion: hook para que Vehiculo envie los faros del automovil.
        enviarFarosVehiculo();
    }

    // 2. Iluminacion: metodo que Vehiculo puede sobrescribir para enviar faros.
    // 5. Trafico autonomo: ahora se envian todos los faros (jugador + autos autonomos).
    protected void enviarFarosVehiculo() {
        entero("uNumFaros", 0);
        decimal("uFarosIntensidad", 0.0f);
    }

    // 2. Iluminacion: activa/desactiva el componente emisivo del material actual.
    protected void emision(float valor) {
        decimal("uEmision", valor);
    }

    // ===================== Ciudad a partir de la matriz ===================
    protected static float centro(int indice) {
        return -LIMITE + CELDA * (indice + 0.5f);
    }

    protected void dibujarFrame() {
        // 3. Minimapa: vista principal ocupa toda la ventana.
        glViewport(0, 0, ancho, alto);
        glClearColor(0.12f, 0.20f, 0.30f, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glUseProgram(programa);
        glBindVertexArray(vao);
        entero("uMapa", 0);
        configurarCamara();
        prepararLuces();
        escena();

        // 3. Minimapa: vista superior en la esquina superior derecha.
        if (mostrarMinimapa) {
            dibujarMinimapa();
        }

        if (glGetError() != GL_NO_ERROR) {
            throw new IllegalStateException("Error OpenGL al dibujar");
        }
    }

    // 3. Minimapa: dibuja la ciudad desde arriba en un recuadro de la esquina superior derecha.
    // Se limpia solo la profundidad de esa zona para que el minimapa se dibuje encima de la vista principal.
    protected void dibujarMinimapa() {
        int miniTamaño = Math.min(ancho, alto) / 4;
        int miniX = ancho - miniTamaño - 20;
        int miniY = alto - miniTamaño - 20;

        // Limpiar solo la profundidad del area del minimapa (scissor) para no borrar la vista principal.
        glEnable(GL_SCISSOR_TEST);
        glScissor(miniX, miniY, miniTamaño, miniTamaño);
        glClear(GL_DEPTH_BUFFER_BIT);
        glDisable(GL_SCISSOR_TEST);

        // Viewport cuadrado para mantener proporciones al redimensionar.
        glViewport(miniX, miniY, miniTamaño, miniTamaño);

        // uMapa=1 activa la proyeccion ortografica superior del vertex shader.
        entero("uMapa", 1);
        escena();

        // Marcadores del auto y destino (sobrescribibles desde Vehiculo).
        dibujarMarcadoresMinimapa();

        // Restaurar viewport principal para el siguiente cuadro.
        glViewport(0, 0, ancho, alto);
    }

    // 3. Minimapa: hook para que Vehiculo dibuje su posicion/orientacion y destino.
    protected void dibujarMarcadoresMinimapa() {
        // Base: no dibuja nada. Vehiculo lo sobrescribe.
    }

    protected void escena() {
        // PUNTO 1: el suelo crece de 70x70 a 110x110 para cubrir el mapa 11x11.
        caja(0, -0.25f, 0, 110, 0.5f, 110, 0.16f, 0.19f, 0.23f);

        // 2. Iluminacion: dibujar las farolas sobre las calles.
        dibujarFarolas();
        // 3. Decoracion urbana: semaforos animados en cruces principales.
        dibujarSemaforos();
        // 3. Decoracion urbana: pasos peatonales junto a cada manzana.
        dibujarPasosPeatonales();
        for (int fila = 0; fila < MAPA.length; fila++) {
            for (int columna = 0; columna < MAPA[fila].length; columna++) {
                float x = centro(columna);
                float z = centro(fila);
                int tipo = MAPA[fila][columna];
                if (tipo == 0) {
                    dibujarMarcasCalle(fila, columna, x, z);
                } else {
                    caja(x, 0.15f, z, 10, 0.3f, 10, 0.60f, 0.64f, 0.66f);
                    if (tipo == 1) {
                        float altura = 5 + (fila * 3 + columna * 7) % 9;
                        float rojo = 0.28f + columna * 0.045f;
                        float azul = 0.48f + fila * 0.025f;
                        cajaConVentanas(x, altura / 2 + 0.3f, z, 7, altura, 7, rojo, 0.40f, azul);
                        caja(x, altura + 0.45f, z, 7.3f, 0.3f, 7.3f, 0.20f, 0.26f, 0.32f);
                    } else if (tipo == 2){
                        dibujarParque(x, z);
                    }else {
                    	dibujarRascacielos(x, z);
                    }
                }
            }
        }
    }
    
    private void dibujarRascacielos(float x, float z) {
        // 1. Cuerpo principal: 30 unidades de altura
        caja(x, 15.0f, z,
             2.0f, 30.0f, 2.0f,
             0.18f, 0.30f, 0.42f);

        // 2. Fachada frontal de vidrio
        caja(x, 15.0f, z + 1.015f,
             1.65f, 29.4f, 0.04f,
             0.12f, 0.55f, 0.78f);

        // 3. Fachada trasera
        caja(x, 15.0f, z - 1.015f,
             1.65f, 29.4f, 0.04f,
             0.12f, 0.48f, 0.70f);

        // 4. Laterales de vidrio
        caja(x + 1.015f, 15.0f, z,
             0.04f, 29.4f, 1.65f,
             0.10f, 0.42f, 0.65f);

        caja(x - 1.015f, 15.0f, z,
             0.04f, 29.4f, 1.65f,
             0.10f, 0.42f, 0.65f);

        // 5. Divisiones horizontales de los pisos
        for (int i = 1; i < 30; i++) {
            float y = i;

            caja(x, y, z + 1.04f,
                 1.85f, 0.035f, 0.025f,
                 0.65f, 0.75f, 0.82f);

            caja(x, y, z - 1.04f,
                 1.85f, 0.035f, 0.025f,
                 0.65f, 0.75f, 0.82f);

            caja(x + 1.04f, y, z,
                 0.025f, 0.035f, 1.85f,
                 0.65f, 0.75f, 0.82f);

            caja(x - 1.04f, y, z,
                 0.025f, 0.035f, 1.85f,
                 0.65f, 0.75f, 0.82f);
        }

        // 6. Corona superior
        caja(x, 30.2f, z,
             2.25f, 0.4f, 2.25f,
             0.12f, 0.18f, 0.25f);

        // 7. Torre de la antena
        caja(x, 31.5f, z,
             0.12f, 2.4f, 0.12f,
             0.75f, 0.78f, 0.82f);

        // 8. Luz de señalización en la punta
        caja(x, 32.8f, z,
             0.22f, 0.22f, 0.22f,
             1.0f, 0.05f, 0.02f);
    }

    // 3. Decoracion urbana: pasos peatonales en los bordes de edificios y parques.
    // Se dibuja un cruce en cada lado de cada manzana, hacia la calle adyacente.
    protected void dibujarPasosPeatonales() {
        for (int fila = 0; fila < MAPA.length; fila++) {
            for (int col = 0; col < MAPA[fila].length; col++) {
                if (MAPA[fila][col] == 0) {
                    continue; // solo junto a manzanas
                }
                float cx = centro(col);
                float cz = centro(fila);
                // Borde hacia la calle superior (horizontal, direccion X)
                // 3. Decoracion urbana: los pasos se colocan 1.0 unidad hacia la calle
                // para que queden centrados en la zona de interseccion.
                float avance = 1.0f;
                // Borde hacia la calle superior (horizontal, direccion X)
                if (fila > 0 && MAPA[fila - 1][col] == 0) {
                    dibujarBandasPeatonales(cx, cz - 5.0f - avance, true);
                }
                // Borde hacia la calle inferior (horizontal, direccion X)
                if (fila < MAPA.length - 1 && MAPA[fila + 1][col] == 0) {
                    dibujarBandasPeatonales(cx, cz + 5.0f + avance, true);
                }
                // Borde hacia la calle izquierda (vertical, direccion Z)
                if (col > 0 && MAPA[fila][col - 1] == 0) {
                    dibujarBandasPeatonales(cx - 5.0f - avance, cz, false);
                }
                // Borde hacia la calle derecha (vertical, direccion Z)
                if (col < MAPA[fila].length - 1 && MAPA[fila][col + 1] == 0) {
                    dibujarBandasPeatonales(cx + 5.0f + avance, cz, false);
                }
            }
        }
    }

    // 3. Decoracion urbana: bandas blancas de un paso peatonal.
    // horizontal=true: paso cruza una calle en direccion X (bandas paralelas a Z).
    // horizontal=false: paso cruza una calle en direccion Z (bandas paralelas a X).
    private void dibujarBandasPeatonales(float x, float z, boolean horizontal) {
        float anchoBanda = 0.45f;
        float separacion = 0.70f;
        float y = 0.02f;
        float alto = 0.02f;
        // El paso es un tramo corto centrado en la acera, no cruza toda la calle.
        float anchoPaso = 3.5f;
        for (int i = -3; i <= 3; i++) {
            float offset = i * separacion;
            if (horizontal) {
                caja(x + offset, y, z, anchoBanda, alto, anchoPaso, 1, 1, 1);
            } else {
                caja(x, y, z + offset, anchoPaso, alto, anchoBanda, 1, 1, 1);
            }
        }
    }

    private void dibujarMarcasCalle(int fila, int columna, float x, float z) {
        // Las lineas amarillas usan emision para simular pintura reflectante en el asfalto.
        emision(0.3f);
        if (fila % 2 == 0 && columna % 2 == 1) {
            for (int desplazamiento = -3; desplazamiento <= 3; desplazamiento += 3) {
                caja(x + desplazamiento, 0.03f, z, 1.6f, 0.04f, 0.13f, 1, 0.84f, 0.35f);
            }
        }

        if (columna % 2 == 0 && fila % 2 == 1) {
            for (int desplazamiento = -3; desplazamiento <= 3; desplazamiento += 3) {
                caja(x, 0.03f, z + desplazamiento, 0.13f, 0.04f, 1.6f, 1, 0.84f, 0.35f);
            }
        }
        emision(0.0f);
    }

    // ===================== Parque: lago, cancha y bancos ===================
    /*
private void dibujarParque(float x, float z) {
        // Base verde del parque
        caja(x, 0.32f, z, 9, 0.1f, 9, 0.20f, 0.45f, 0.28f);

        // --- Lago ---
        float lagoX = x + 2.5f;
        float lagoZ = z + 2.5f;
        // El lago se dibuja por encima de la base verde para evitar que quede oculto.
        caja(lagoX, 0.33f, lagoZ, 2.5f, 0.06f, 2.0f, 0.18f, 0.42f, 0.78f);
        // Pequena isla en el lago
        caja(lagoX, 0.35f, lagoZ, 0.7f, 0.08f, 0.7f, 0.35f, 0.55f, 0.25f);

        // --- Cancha de futsal ---
        float canchaX = x - 1.5f;
        float canchaZ = z - 1.0f;
        float canchaAncho = 4.0f;
        float canchaLargo = 2.5f;
        // Suelo de la cancha (ligeramente mas bajo para evitar z-fighting con las lineas)
        caja(canchaX, 0.32f, canchaZ, canchaAncho, 0.04f, canchaLargo, 0.55f, 0.50f, 0.45f);
        // Lineas blancas (mas gruesas y altas para que se vean mejor)
        float lineaY = 0.35f;
        float lineaAlto = 0.02f;
        float lineaGrosor = 0.12f;
        // Lineas de fondo (largas, paralelas al eje X)
        caja(canchaX, lineaY, canchaZ - canchaLargo / 2 + lineaGrosor / 2,
                canchaAncho, lineaAlto, lineaGrosor, 1, 1, 1);
        caja(canchaX, lineaY, canchaZ + canchaLargo / 2 - lineaGrosor / 2,
                canchaAncho, lineaAlto, lineaGrosor, 1, 1, 1);
        // Lineas de extremo (cortas, paralelas al eje Z)
        caja(canchaX - canchaAncho / 2 + lineaGrosor / 2, lineaY, canchaZ,
                lineaGrosor, lineaAlto, canchaLargo, 1, 1, 1);
        caja(canchaX + canchaAncho / 2 - lineaGrosor / 2, lineaY, canchaZ,
                lineaGrosor, lineaAlto, canchaLargo, 1, 1, 1);
        // Linea central
        caja(canchaX, lineaY, canchaZ, lineaGrosor, lineaAlto, canchaLargo, 1, 1, 1);
        // Areas de porteria
        float areaAncho = 1.0f;
        float areaLargo = 0.8f;
        caja(canchaX - canchaAncho / 2 + areaAncho / 2, lineaY, canchaZ,
                areaAncho, lineaAlto, areaLargo, 1, 1, 1);
        caja(canchaX + canchaAncho / 2 - areaAncho / 2, lineaY, canchaZ,
                areaAncho, lineaAlto, areaLargo, 1, 1, 1);
        // Porterias
        caja(canchaX - canchaAncho / 2 - 0.15f, lineaY + 0.08f, canchaZ,
                0.2f, 0.2f, 0.6f, 0.9f, 0.9f, 0.9f);
        caja(canchaX + canchaAncho / 2 + 0.15f, lineaY + 0.08f, canchaZ,
                0.2f, 0.2f, 0.6f, 0.9f, 0.9f, 0.9f);

        // --- Caminito de piedra entre lago y cancha ---
        float caminoY = 0.37f;
        float caminoAlto = 0.03f;
        // Tramo horizontal: desde el borde del lago hasta la esquina del camino
        caja(x + 0.75f, caminoY, z + 1.6f, 1.0f, caminoAlto, 0.8f, 0.58f, 0.55f, 0.52f);
        // Tramo vertical: desde la esquina hasta el borde de la cancha
        caja(x + 0.5f, caminoY, z + 0.9f, 0.8f, caminoAlto, 1.4f, 0.58f, 0.55f, 0.52f);

        // --- Bancos ---
        // Se aumentan de tamano y se distribuyen mas para que se noten en el parque.
        banco(x - 3.5f, 0.40f, z + 3.5f, -0.785f);
        banco(x - 3.5f, 0.40f, z - 3.5f, 0.785f);
        banco(x + 3.5f, 0.40f, z - 3.5f, 2.356f);
        banco(x + 3.5f, 0.40f, z + 3.5f, 3.927f);
        banco(x + 0.5f, 0.40f, z + 3.9f, 0.0f);
        banco(x - 0.5f, 0.40f, z - 3.9f, 3.142f);

        // --- Arboles ---
        // Arboles mas grandes y distribuidos por todo el parque.
        arbol(x - 3.0f, z + 2.5f);
        arbol(x + 2.5f, z - 3.0f);
        arbol(x + 3.0f, z + 3.0f);
        arbol(x - 2.5f, z - 2.5f);
        arbol(x + 0.5f, z + 3.2f);
    }
    */

private void dibujarParque(float x, float z) {

    // =================================================
    // 1. TERRENO Y BORDES DEL PARQUE
    // =================================================
    caja(x, 0.32f, z, 9.0f, 0.10f, 9.0f,
         0.22f, 0.48f, 0.25f);

    // Bordes decorativos de piedra
    caja(x, 0.38f, z - 4.35f, 8.8f, 0.08f, 0.15f,
         0.55f, 0.52f, 0.44f);
    caja(x, 0.38f, z + 4.35f, 8.8f, 0.08f, 0.15f,
         0.55f, 0.52f, 0.44f);
    caja(x - 4.35f, 0.38f, z, 0.15f, 0.08f, 8.8f,
         0.55f, 0.52f, 0.44f);
    caja(x + 4.35f, 0.38f, z, 0.15f, 0.08f, 8.8f,
         0.55f, 0.52f, 0.44f);


    // =================================================
    // 2. LAGO DECORATIVO
    // =================================================
    float lagoX = x + 2.45f;
    float lagoZ = z + 2.35f;

    // Borde del lago
    caja(lagoX, 0.37f, lagoZ, 2.85f, 0.12f, 2.35f,
         0.68f, 0.64f, 0.48f);

    // Agua
    caja(lagoX, 0.44f, lagoZ, 2.60f, 0.06f, 2.10f,
         0.10f, 0.48f, 0.82f);

    // Isla central
    caja(lagoX, 0.49f, lagoZ, 0.75f, 0.10f, 0.65f,
         0.30f, 0.58f, 0.25f);

    // Pequeña fuente central
    caja(lagoX, 0.59f, lagoZ, 0.20f, 0.18f, 0.20f,
         0.75f, 0.78f, 0.80f);

    caja(lagoX, 0.79f, lagoZ, 0.10f, 0.25f, 0.10f,
         0.35f, 0.72f, 0.95f);


    // =================================================
    // 3. CANCHA DE FUTSAL
    // =================================================
    float canchaX = x - 1.50f;
    float canchaZ = z - 1.00f;
    float ancho = 4.00f;
    float largo = 2.50f;
    float lineaY = 0.36f;
    float grosor = 0.08f;

    // Superficie de la cancha
    caja(canchaX, 0.34f, canchaZ, ancho, 0.06f, largo,
         0.24f, 0.48f, 0.30f);

    // Líneas exteriores
    caja(canchaX, lineaY, canchaZ - largo / 2,
         ancho, 0.025f, grosor, 0.95f, 0.95f, 0.88f);

    caja(canchaX, lineaY, canchaZ + largo / 2,
         ancho, 0.025f, grosor, 0.95f, 0.95f, 0.88f);

    caja(canchaX - ancho / 2, lineaY, canchaZ,
         grosor, 0.025f, largo, 0.95f, 0.95f, 0.88f);

    caja(canchaX + ancho / 2, lineaY, canchaZ,
         grosor, 0.025f, largo, 0.95f, 0.95f, 0.88f);

    // Línea central
    caja(canchaX, lineaY, canchaZ,
         grosor, 0.025f, largo, 0.95f, 0.95f, 0.88f);

    // Porterías
    caja(canchaX - ancho / 2 - 0.12f, 0.48f, canchaZ,
         0.12f, 0.28f, 0.65f, 0.90f, 0.90f, 0.86f);

    caja(canchaX + ancho / 2 + 0.12f, 0.48f, canchaZ,
         0.12f, 0.28f, 0.65f, 0.90f, 0.90f, 0.86f);


    // =================================================
    // 4. SENDERO DE PIEDRA
    // =================================================

    // Sendero alrededor de la zona central
    caja(x + 0.70f, 0.40f, z + 1.15f,
         0.65f, 0.035f, 2.20f,
         0.67f, 0.62f, 0.53f);

    caja(x + 1.50f, 0.40f, z + 0.15f,
         2.10f, 0.035f, 0.60f,
         0.67f, 0.62f, 0.53f);

    // Losas decorativas sobre el sendero
    for (int i = 0; i < 5; i++) {
        caja(x + 0.70f, 0.425f, z + 0.35f + i * 0.38f,
             0.55f, 0.015f, 0.27f,
             0.78f, 0.74f, 0.65f);
    }


    // =================================================
    // 5. JARDINERAS Y FLORES
    // =================================================

    // Jardineras laterales
    caja(x - 3.45f, 0.43f, z + 0.20f,
         1.00f, 0.18f, 2.00f,
         0.38f, 0.24f, 0.14f);

    caja(x + 3.45f, 0.43f, z - 1.50f,
         1.00f, 0.18f, 1.60f,
         0.38f, 0.24f, 0.14f);

    // Tierra de las jardineras
    caja(x - 3.45f, 0.53f, z + 0.20f,
         0.86f, 0.05f, 1.80f,
         0.20f, 0.14f, 0.08f);

    caja(x + 3.45f, 0.53f, z - 1.50f,
         0.86f, 0.05f, 1.40f,
         0.20f, 0.14f, 0.08f);

    // Flores de colores
    flor(x - 3.45f, z - 0.45f, 0.90f, 0.20f, 0.35f);
    flor(x - 3.45f, z + 0.20f, 1.00f, 0.75f, 0.15f);
    flor(x - 3.45f, z + 0.80f, 0.80f, 0.25f, 0.90f);

    flor(x + 3.45f, z - 1.95f, 1.00f, 0.35f, 0.20f);
    flor(x + 3.45f, z - 1.50f, 0.95f, 0.20f, 0.40f);
    flor(x + 3.45f, z - 1.05f, 1.00f, 0.80f, 0.15f);


    // =================================================
    // 6. ARBUSTOS DECORATIVOS
    // =================================================
    caja(x - 1.80f, 0.58f, z + 3.65f,
         0.75f, 0.40f, 0.65f,
         0.13f, 0.38f, 0.16f);

    caja(x + 1.80f, 0.58f, z - 3.65f,
         0.75f, 0.40f, 0.65f,
         0.13f, 0.38f, 0.16f);


    // =================================================
    // 7. BANCOS
    // =================================================
    banco(x - 3.40f, 0.43f, z + 3.40f, -0.785f);
    banco(x - 3.40f, 0.43f, z - 3.40f, 0.785f);
    banco(x + 3.40f, 0.43f, z + 3.40f, 3.927f);
    banco(x + 0.20f, 0.43f, z - 3.90f, 3.142f);


    // =================================================
    // 8. ÁRBOLES
    // =================================================
    arbol(x - 3.00f, z + 2.00f);
    arbol(x + 2.80f, z - 3.00f);
    arbol(x + 3.10f, z + 3.00f);
    arbol(x - 2.80f, z - 2.80f);


    // =================================================
    // 9. FAROLAS
    // =================================================
    farolaParque(x - 3.70f, z);
    farolaParque(x + 3.70f, z + 1.50f);
}

    
    // 3. Decoracion urbana: semaforos en una esquina de cada edificio.
    // Se colocan en la esquina noreste de cada manzana y apuntan hacia la interseccion.
    protected void dibujarSemaforos() {
        int desfase = 0;
        for (int fila = 0; fila < MAPA.length; fila++) {
            for (int col = 0; col < MAPA[fila].length; col++) {
                if (MAPA[fila][col] == 1) { // edificio
                    float cx = centro(col);
                    float cz = centro(fila);
                    // Esquina noreste, ligeramente hacia la acera.
                    float x = cx + 3.8f;
                    float z = cz + 3.8f;
                    // Angulo -0.785 rad para que el semaforo apunte hacia el noreste (interseccion).
                    dibujarSemaforoGirado(x, z, -0.785f, desfase);
                    desfase++;
                }
            }
        }
    }


    // 3. Decoracion urbana: semaforo con poste, caja y luces giradas por angulo.
    private void dibujarSemaforoGirado(float x, float z, float angulo, int desfase) {
        float seno = (float) Math.sin(angulo);
        float coseno = (float) Math.cos(angulo);

        // Poste vertical (no cambia al girar alrededor de Y).
        cajaGirada(x, 2.75f, z, 0.15f, 5.5f, 0.15f, 0.25f, 0.25f, 0.25f, angulo);
        // Caja del semaforo (girada para dar a la calle).
        cajaGirada(x, 5.0f, z, 0.5f, 1.2f, 0.3f, 0.15f, 0.15f, 0.15f, angulo);

        // Ciclo de 8 segundos con desfase para que no esten todos sincronizados.
        double tiempo = glfwGetTime();
        float fase = (float) ((tiempo + desfase * 0.7) % 8.0);
        boolean rojo = false, amarillo = false, verde = false;
        if (fase < 3.0f) {
            rojo = true;
        } else if (fase < 4.0f) {
            rojo = true;
            amarillo = true;
        } else if (fase < 7.0f) {
            verde = true;
        } else {
            amarillo = true;
        }

        // Offset local de cada luz respecto al centro del semaforo.
        // Tras la rotacion, +Z local apunta hacia la interseccion.
        float[][] luces = {
                { 0.0f, 0.45f, 0.16f },   // Roja (arriba)
                { 0.0f, 0.0f, 0.16f },    // Amarilla (centro)
                { 0.0f, -0.45f, 0.16f }   // Verde (abajo)
        };
        boolean[] encendidas = { rojo, amarillo, verde };
        float[][] coloresApagado = {
                { 0.15f, 0.0f, 0.0f },
                { 0.15f, 0.15f, 0.0f },
                { 0.0f, 0.15f, 0.0f }
        };
        float[][] coloresEncendido = {
                { 1.0f, 0.0f, 0.0f },
                { 1.0f, 0.85f, 0.0f },
                { 0.0f, 1.0f, 0.0f }
        };

        for (int i = 0; i < 3; i++) {
            float lx = luces[i][0];
            float ly = luces[i][1];
            float lz = luces[i][2];
            // Mismo paso de local a mundo que usa pieza() en Vehiculo.
            float mundoX = x + coseno * lx + seno * lz;
            float mundoZ = z - seno * lx + coseno * lz;

            emision(encendidas[i] ? 0.8f : 0.0f);
            float[] color = encendidas[i] ? coloresEncendido[i] : coloresApagado[i];
            cajaGirada(mundoX, 5.0f + ly, mundoZ, 0.25f, 0.25f, 0.06f,
                    color[0], color[1], color[2], angulo);
        }
        emision(0.0f);
    }

    // 2. Iluminacion: dibuja postes y bombillas de las farolas.
    // La bombilla usa emision para verse brillante; la luz real se calcula en el shader.
    protected void dibujarFarolas() {
        for (float[] f : farolas) {
            float x = f[0];
            float z = f[2];
            // Poste metalico oscuro
            caja(x, 2.5f, z, 0.15f, 5.0f, 0.15f, 0.25f, 0.25f, 0.25f);
            // Bombilla emisiva (brilla por si misma)
            emision(0.7f);
            caja(x, 5.0f, z, 0.40f, 0.15f, 0.40f, 1.00f, 0.90f, 0.50f);
            emision(0.0f);
        }
    }

    private void banco(float x, float y, float z, float angulo) {
        // 3. Decoracion urbana: bancos mas grandes para que se noten desde lejos.
        float sin = (float) Math.sin(angulo);
        float cos = (float) Math.cos(angulo);
        // Asiento
        cajaGirada(x, y, z, 1.30f, 0.15f, 0.50f, 0.55f, 0.35f, 0.20f, angulo);
        // Respaldo
        float dx = 0.22f * sin;
        float dz = -0.22f * cos;
    }

    private void arbol(float x, float z) {
        // 3. Decoracion urbana: arboles mas grandes y con copa mas ancha.
        // Tronco
        caja(x, 0.60f, z, 0.45f, 1.00f, 0.45f, 0.40f, 0.25f, 0.15f);
        // Copa (dos niveles para dar forma)
        caja(x, 1.20f, z, 1.80f, 1.00f, 1.80f, 0.22f, 0.55f, 0.22f);
        caja(x, 1.80f, z, 1.20f, 0.80f, 1.20f, 0.25f, 0.60f, 0.25f);
    }
    

private void flor(float x, float z, float r, float g, float b) {

    // Tallo
    caja(x, 0.61f, z, 0.06f, 0.20f, 0.06f, 0.12f, 0.38f, 0.12f);

    // Centro de la flor
    caja(x, 0.73f, z, 0.12f, 0.06f, 0.12f, 1.00f, 0.80f, 0.15f);

    // Pétalos
    caja(x + 0.12f, 0.73f, z, 0.12f, 0.05f, 0.12f, r, g, b);

    caja(x - 0.12f, 0.73f, z, 0.12f, 0.05f, 0.12f, r, g, b);

    caja(x, 0.73f, z + 0.12f, 0.12f, 0.05f, 0.12f, r, g, b);

    caja(x, 0.73f, z - 0.12f, 0.12f, 0.05f, 0.12f, r, g, b);
}


private void farolaParque(float x, float z) {

    // Base de piedra
    caja(x, 0.43f, z, 0.32f, 0.16f, 0.32f, 0.30f, 0.30f, 0.30f);

    // Poste metálico
    caja(x, 1.55f, z, 0.12f, 2.20f, 0.12f, 0.18f, 0.19f, 0.20f);

    // Soporte superior
    caja(x, 2.70f, z, 0.35f, 0.10f, 0.35f, 0.18f, 0.19f, 0.20f);

    // Lámpara
    emision(0.65f);

    caja(x, 2.85f, z, 0.28f, 0.18f, 0.28f, 1.00f, 0.86f, 0.48f);

    emision(0.0f);
}


    // ===================== Dibujar cajas y enviar uniform ===================
    protected void caja(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b) {
        cajaGirada(x, y, z, sx, sy, sz, r, g, b, 0);
    }

    protected void cajaConVentanas(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b) {
        entero("uVentanas", 1);
        caja(x, y, z, sx, sy, sz, r, g, b);
        entero("uVentanas", 0);
    }

    protected void cajaGirada(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b,
            float angulo) {
        vector("uPos", x, y, z);
        vector("uEscala", sx, sy, sz);
        vector("uColor", r, g, b);
        decimal("uGiro", angulo);
        glDrawArrays(GL_TRIANGLES, 0, 36);
    }

    private int uniform(String nombre) {
        if (!uniforms.containsKey(nombre)) {
            int ubicacion = glGetUniformLocation(programa, nombre);
            uniforms.put(nombre, ubicacion);
        }
        return uniforms.get(nombre);
    }

    protected void vector(String nombre, float x, float y, float z) {
        glUniform3f(uniform(nombre), x, y, z);
    }

    protected void decimal(String nombre, float valor) {
        glUniform1f(uniform(nombre), valor);
    }

    protected void entero(String nombre, int valor) {
        glUniform1i(uniform(nombre), valor);
    }

    // ===================== Shaders: Codigo que se ejecuta en la GPU
    // ===================
    private String vertexShader() {
        return """
                    #version 330 core // Seleccionamos GLSL 3.30
                    layout (location = 0) in vec3 aPos; // Leemos la posicion local del vertice desde el VBO
                    layout (location = 1)  in vec3 aNormal; // Leemos la normal de la cara desde el mismo VBO
                    uniform vec3 uPos; // Recibe el centro e la caja de la ciudad
                    uniform vec3 uEscala; // Recibe el tamaño de la caja en cada eje
                    uniform vec3 uOjo; // Recibe la posicion de la camara
                    uniform vec3 uObjetivo; // Punto que observa la camara
                    uniform float uGiro; // Recibe el giro del objeto alrededor de Y
                    uniform float uAspecto; // Recibe la relacion del ancho y alto de la imagen
                    uniform int uMapa; // Seleccionamos la perspectiva (0, 1)
                    out vec3 vMundo; // La posicion mundial al shader de fragmentos
                    out vec3 vNormal; // Enviamos la normal transformada par ala iluminacion

                    void main() {
                        float coseno = cos(uGiro); // Calculamos el coseno del giro del objeto
                        float seno = sin(uGiro);
                        mat3 giro = mat3(
                            coseno, 0.0,    -seno,
                            0.0,    1.0,    0.0,
                            seno,   0.0,    coseno
                        );
                        vMundo = giro * (aPos * uEscala) + uPos; // Escalar, firar y trasladar el vertice al mundo
                        vNormal = normalize(giro * (aNormal / uEscala)); // Corregimos la normal con la inversa transpuesta de la escala y giro

                        if (uMapa == 1) { //Esta rama se usa al dibujar el minimapa
                            // PUNTO 1: escala del minimapa ajustada al nuevo LIMITE (55).
                            // Se usa 57.0 para dejar un pequeno margen alrededor de la ciudad.
                            float pantallaX = vMundo.x / 57.0;
                            float pantallaY = vMundo.z / 57.0;
                            float profundidad = -vMundo.y / 100.0;
                            gl_Position = vec4(pantallaX, pantallaY, profundidad, 1.0);
                        } else { // La escena principal utilizando la camara como perspectiva
                            vec3 frente = normalize(uObjetivo - uOjo); // Calcular la direccion hacia lo que mira la camara
                            vec3 derecha = normalize(cross(frente, vec3(0.0, 1.0, 0.0))); // Obtenemos el eje horizontal de la camra
                            vec3 arriba = cross(derecha, frente); // Obtenemos el eje vertical
                            vec3 diferencia = vMundo - uOjo; // Trasladamos el origen del mundo hasta la camara
                            float vistaX = dot(diferencia, derecha);
                            float vistaY = dot(diferencia, arriba);
                            float vistaZ = -dot(diferencia, frente);
                            float factor = 1.0 / tan(radians(55.0) * 0.5); // Convertimos el campo visual (55 grados) en escala de perspectiva
                            float cerca = 0.1;
                            float lejos = 250.0;
                            float clipX = vistaX * factor / uAspecto;
                            float clipY = vistaY * factor;
                            float clipZ = (lejos + cerca) / (cerca - lejos) * vistaZ;
                            clipZ += 2.0 * lejos * cerca / (cerca - lejos); // añadir el constante de profundidad
                            gl_Position = vec4(clipX, clipY, clipZ, -vistaZ);
                        }
                    }
                """;
    }

    private String fragmentShader() {
        return """
                    #version 330 core
                    uniform vec3 uColor;
                    uniform vec3 uPos;
                    uniform vec3 uEscala;
                    uniform int uVentanas;
                    // 2. Iluminacion: uniformes de luces.
                    // uFarolasPos/Color se declaran con 25 elementos para cubrir las 19 farolas generadas
                    // y evitar lecturas fuera de limites que provocan luces intermitentes.
                    uniform vec3 uAmbiente;
                    uniform vec3 uDireccionalDir;
                    uniform vec3 uDireccionalColor;
                    uniform vec3 uFarolasPos[25];
                    uniform vec3 uFarolasColor[25];
                    uniform int uNumFarolas;
                    // 5. Trafico autonomo: 22 faros = 2 del jugador + 2 por cada uno de los 10 autos autonomos.
                    uniform vec3 uFarosPos[22];
                    uniform vec3 uFarosDir[22];
                    uniform vec3 uFarosColor[22];
                    uniform float uFarosCorte;
                    uniform int uNumFaros;
                    uniform float uFarosIntensidad;
                    uniform float uEmision;
                    // 3. Decoracion urbana: modo nocturno para ventanas iluminadas.
                    uniform int uModoNocturno;
                    in vec3 vMundo;
                    in vec3 vNormal;
                    out vec4 color;

                    void main() {
                        vec3 c = uColor;
                        // 3. Decoracion urbana: ventana y puerta se declaran aqui para usarse despues
                        // en la emision nocturna de ventanas.
                        bool ventana = false;
                        bool puerta = false;
                        if (uVentanas == 1 && abs(vNormal.y) < 0.5) {
                            vec3 local = (vMundo - uPos) / (uEscala * 0.5);
                            float u, v;
                            if (abs(vNormal.x) > 0.5) {
                                u = local.z;
                                v = local.y;
                            } else {
                                u = local.x;
                                v = local.y;
                            }
                            u = u * 0.5 + 0.5;
                            v = v * 0.5 + 0.5;

                            float puertaAncho = 0.35;
                            float puertaAlto = 0.30;
                            puerta = abs(u - 0.5) < puertaAncho * 0.5 && v < puertaAlto;

                            if (!puerta && v > puertaAlto + 0.05) {
                                int columnas = 2;
                                int filas = 3;
                                float marco = 0.06;
                                float anchoUtil = 1.0 - 2.0 * marco;
                                float altoUtil = 1.0 - puertaAlto - 2.0 * marco;
                                float anchoVentana = (anchoUtil / float(columnas)) - marco;
                                float altoVentana = (altoUtil / float(filas)) - marco;
                                for (int col = 0; col < columnas; col++) {
                                    for (int fila = 0; fila < filas; fila++) {
                                        float cx = marco + anchoVentana * 0.5 + float(col) * (anchoVentana + marco);
                                        float cy = puertaAlto + marco + altoVentana * 0.5 + float(fila) * (altoVentana + marco);
                                        if (abs(u - cx) < anchoVentana * 0.5 && abs(v - cy) < altoVentana * 0.5) {
                                            ventana = true;
                                        }
                                    }
                                }
                            }

                            if (puerta) {
                                c = vec3(0.30, 0.20, 0.12);
                            } else if (ventana) {
                                // 3. Decoracion urbana: de noche las ventanas se ven iluminadas.
                                if (uModoNocturno == 1) {
                                    c = vec3(1.00, 0.95, 0.65);
                                } else {
                                    c = vec3(0.75, 0.85, 0.95);
                                }
                            }
                        }

                        // 2. Iluminacion: calculo de luz difusa sobre la superficie.
                        vec3 normal = normalize(vNormal);
                        vec3 luzTotal = uAmbiente;

                        // Luz direccional (sol/luna).
                        float diffDir = max(dot(normal, -normalize(uDireccionalDir)), 0.0);
                        luzTotal += uDireccionalColor * diffDir;

                        // Luces puntuales de las farolas, con atenuacion por distancia.
                        // Atenuacion suave para que iluminen bien la calle sin quemar de cerca.
                        for (int i = 0; i < uNumFarolas; i++) {
                            vec3 aLuz = uFarolasPos[i] - vMundo;
                            float dist = max(length(aLuz), 0.5);
                            vec3 L = normalize(aLuz);
                            float diff = max(dot(normal, L), 0.0);
                            float atenuacion = 1.0 / (1.0 + 0.03 * dist + 0.008 * dist * dist);
                            luzTotal += uFarolasColor[i] * diff * atenuacion;
                        }

                        // 5. Trafico autonomo: faros del jugador y de todos los autos autonomos.
                        // Los dos primeros faros son del jugador y usan uFarosIntensidad (0..1).
                        // Los demas pertenecen a los autos autonomos y estan siempre encendidos.
                        for (int i = 0; i < uNumFaros; i++) {
                            vec3 aLuz = uFarosPos[i] - vMundo;
                            // Evita brillos excesivos cuando el auto esta muy cerca de una superficie.
                            float dist = max(length(aLuz), 0.5);
                            vec3 L = normalize(aLuz);
                            vec3 D = normalize(uFarosDir[i]);
                            float coseno = dot(L, -D);
                            if (coseno > uFarosCorte) {
                                float diff = max(dot(normal, L), 0.0);
                                float atenuacion = 1.0 / (1.0 + 0.15 * dist + 0.08 * dist * dist);
                                // smoothstep suaviza el borde del foco y reduce parpadeo intermitente.
                                float intensidad = smoothstep(uFarosCorte, 1.0, coseno);
                                float intensidadFaro = (i < 2) ? uFarosIntensidad : 1.0;
                                luzTotal += uFarosColor[i] * diff * atenuacion * intensidad * intensidadFaro;
                            }
                        }

                        // Se limita la luz total para evitar que se queme la imagen (blowout).
                        // El limite se relaja un poco para que las farolas iluminen de noche.
                        luzTotal = min(luzTotal, vec3(1.8));
                        // 3. Decoracion urbana: emision adicional para ventanas iluminadas de noche.
                        float emisionVentana = 0.0;
                        if (uVentanas == 1 && ventana && uModoNocturno == 1) {
                            emisionVentana = 0.35;
                        }
                        // Color final: luz difusa mas emision propia (bombillas/faros/ventanas).
                        color = vec4(c * luzTotal + c * uEmision + vec3(emisionVentana), 1.0);
                    }
                """;
    }
}
