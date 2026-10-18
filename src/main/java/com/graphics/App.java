package com.graphics;

import java.nio.FloatBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Este programa hace 3 cosas
 * 1. abrir una ventaba usando la biblioteca GLFW
 * 2. Definir un triangulo y usos shaders
 * 3. En un bucle, dibujar el triangulo en la windows una y otra vez hasta que
 * el usuario cierre la windows
 *
 * CONCEPTOS CLAVE:
 * -OPENGL ES UNA API DE GRAFICOS 3D
 * -GLFW ES UNA BIBLIOTECA QUE NOS PERMITE CREAR windowsS Y RECIBIR INPUT DEL
 * USUARIO
 * -SHADERS SON PROGRAMAS QUE SE EJECUTAN EN LA GPU Y NOS PERMITEN DIBUJAR COSAS
 * EN LA PANTALLA
 * -GPU ES UN PROCESADOR DE GRAFICOS QUE NOS PERMITE DIBUJAR COSAS EN LA
 * PANTALLA
 * - VAO/VBO/IBO SON OBJETOS QUE NOS PERMITEN ALMACENAR DATOS EN LA GPU
 * - VAO (Vertex Array Object) NOS PERMITE ALMACENAR LA INFORMACION DE LOS
 * VERTICES DE UN OBJETO
 * - VBO (Vertex Buffer Object) NOS PERMITE ALMACENAR LA INFORMACION DE LOS
 * VERTICES DE UN OBJETO EN LA GPU
 * - IBO (Index Buffer Object) NOS PERMITE ALMACENAR LA INFORMACION DE LOS
 * INDICES DE UN OBJETO EN LA GPU
 */

public class App {

    private long windows; // referencia a nuestra ventana creada con GLFW
    private int programa; // referencia a nuestro programa de shader + fragment shader unidos
    private int vao; // referencia a nuestro Vertex Array Object
    private int vbo; // referencia a nuestro Vertex Buffer Object
    private static final int ANCHO = 800; // ancho de la ventana
    private static final int ALTO = 600; // alto de la ventana

    public static void main(String[] args) {
        new App().run();
    }

    /*
     * Punto de entrada: inicializamos todo, ejecutamos el bucle del dibujo y al
     * salir limpiamos
     */
    public void run() {
        init();
        loop();
        cleanup();

    }

    // Paso 1: inicializar GLFW y crear la ventana
    // GWFW crear y gestiona la ventana del sistema, tambien recibe eventos del
    // teclado, raton, ventaja

    public void init() {

        // Inicializar GLFW
        if (!GLFW.glfwInit()) {
            throw new IllegalStateException("No se pudo inicializar GLFW");
        }

        // Restaurar todas opciones por defecto
        GLFW.glfwDefaultWindowHints();

        // Ocultar la ventana inicialmente
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,  GLFW.GLFW_FALSE);

        // Permitir redimensionar
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE,  GLFW.GLFW_TRUE);

        // OpenGL 3.3
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);

        // Usar OpenGL Core Profile (evitar compatibilidad con codigo muy antiguo)
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);

        // Crear ventana
        windows = GLFW.glfwCreateWindow(ANCHO, ALTO,"OpenGL - Triangulo", 0,0);

        // Hacer que el contexto OpenGL de esta ventana sea el actual
        GLFW.glfwMakeContextCurrent(windows);

        // Sincronización vertical, evitamos el tearing (imagen partida)
        GLFW.glfwSwapInterval(1);

        // Mostrar ventana
        GLFW.glfwShowWindow(windows);

        // Crear las capacidades de OpenGL, LWJGL necestia este paso para enlazar las llamadas de Java con la CPU
        GL.createCapabilities();

        // Crear shaders
        crearShaders();

        // Crear triángulo
        crearTriangulo();
    }

    // bucle principal: en cada iteracion dibujamos un frame
    // mientrar la ventana no se cierre:
    /*
     * 1. limpiamos la ventana
     * 2. Decimos usar este programa de shaders y en este vao para dibujar
     * 3. ordenamos dibujar 3 vertices
     * 4. Intercambiamos los buffers
     * 5. procesamos los eventos
     */

    //bucle principal, en cada iteracion dibujamos un frame 
    // mientras la ventaja no se cierre
    /*
    1. limpiamos la ventana
    2, decimos usar este prograna de shaders y este VAO para dibujar
    3. Ordenamos dibujar los 3 vertices
    4, Intercambiamos los buffers
    5. Procesamos los eventos
    */
    private void loop() {

        // Mientras el usuario no cierre la ventana
        while (!GLFW.glfwWindowShouldClose(windows)) {
            // 1. LIMPIAR LA PANTALLA
            GL11.glClearColor(0.08f, 0.02f, 0.12f, 1.0f);

            //limpiar el buffer de color (rellenar con el color anterior)
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

            // 2. ACTIVAR EL PROGRAMA DE SHADERS
            GL20.glUseProgram(programa);

            // 3. ACTIVAR EL VAO, que contiene el triangulo
            GL30.glBindVertexArray(vao);

            // 4. DIBUJAR LOS 3 VÉRTICES, modo, empezar con el vertice 0 y dibujar 3 vertices
            GL11.glDrawArrays(GL11.GL_TRIANGLES,0,3);

            // 5. INTERCAMBIAR LOS BUFFERS
            GLFW.glfwSwapBuffers(windows);

            // 6. PROCESAR EVENTOS DEL SISTEMA
            GLFW.glfwPollEvents();
        }
    }

    //progrmas que corren en la GPU
    /*
    En openGL la gpu dibuja en 2 etapas:
    1. Vertex shader: se ejecuta por cada vertice (en nuestro triangulo, 3 veces)
    su trabajo es decir donde va ese vertice en la pantalla (posicion 2D/3D)
    2. Frangment shader: se ejecuta por cada pixel que ocupa el trangulo (miles y miles)
    su trabajo es decir de que color se pinta ese pixel.
    Sin el vertex shader, la GPU no sabria donde colocar los puntos. sin el fragment shacer no
    sabria que color dar a cada pixel. por eso se requiere los dos.
    OpenGP su lenguaje de prrogramacion es (OPENGL Shading Language). es parecido a C, porque se usa para programar la CPU
    */

    private int compilarShader(int tipo, String codigoFuente) {
        int shader = GL20.glCreateShader(tipo);
        GL20.glShaderSource(shader, codigoFuente);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("No se pudo compilar el shader: "
                    + GL20.glGetShaderInfoLog(shader));
        }

        return shader;
    }

    private void crearShaders() {
        // vertexShaderSource: codigo GLSL del vertex shader
        String vertexShaderSource = "#version 330 core\n"
                + "layout (location = 0) in vec3 aPos;\n"
                + "void main() {\n"
                + "    gl_Position = vec4(aPos, 1.0);\n"
                + "}";

        // fragmentShaderSource: codigo GLSL del fragment shader
        // fragColor color de salida del pixel, este saca un array vec4(R, G, B, A)
        // este se ejecuta por cada pixel del triangulo, la salida es el colo del pixel
        String fragmentShaderSource = "#version 330 core\n"
                + "out vec4 fragColor;\n"
                + "void main() {\n"
                + "    fragColor = vec4(0.2, 0.8, 0.4, 1.0);\n"
                + "}";

        int vertexShader = compilarShader(GL20.GL_VERTEX_SHADER, vertexShaderSource);
        int fragmentShader = compilarShader(GL20.GL_FRAGMENT_SHADER, fragmentShaderSource);

        //crear el programa (vertex + frament unidos)
        programa = GL20.glCreateProgram();
        GL20.glAttachShader(programa, vertexShader);
        GL20.glAttachShader(programa, fragmentShader);
        GL20.glLinkProgram(programa);

        if (GL20.glGetProgrami(programa, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("No se pudo enlazar el programa de shaders: "
                    + GL20.glGetProgramInfoLog(programa));
        }

        // como ya lo compilo, elimino los shaders para liberarlos
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);
    }

    private void crearTriangulo() {
        // el array de puntos, es para crear vertices, como es un triangulo, son 3 puntos
        // las coordenas es de -1 a 1,   
        // si (-1, -1) = esquina inferior izquierda
        // si (1, 1) = esquina superior derecha
        // si (0, 0) = es el centro
        
        float[] vertices = {
                0.0f, 0.5f,
                -0.5f, -0.5f,
                0.5f, -0.5f
        };

        //vao: objeto que guarda la configuracion de los buffer y atributos
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        //vbo: buffer en la GPU donde guardamos las coordenadas
        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        //OpenGL espera los datos en FloatBuffer, no en un float[] de java
        FloatBuffer buffer = BufferUtils.createFloatBuffer(vertices.length);
        buffer.put(vertices).flip();
        // Enviar los datos al VBO. GL_STATIC_DRAW = "no vamos a cambiar estos datos"
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, buffer, GL15.GL_STATIC_DRAW);



        //GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STATIC_DRAW);
        // decirle a OpenGL como voy a leer este buffer
        /*
        atributo location 0 (como layout location = 0 en el vertex shader)
        2 floats por vertice (x,y)
        tipo Gl_FLOAT, sin normalizar
        stride = 8 bytes (2 floats x 4), offset = 0
        */
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 2 * Float.BYTES, 0);
        // activar el atributo 0
        GL20.glEnableVertexAttribArray(0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);
    }

    
    private void cleanup() {
        GL30.glDeleteVertexArrays(vao);
        GL15.glDeleteBuffers(vbo);
        GL20.glDeleteProgram(programa);
        GLFW.glfwDestroyWindow(windows);
        GLFW.glfwTerminate();
    }

}
