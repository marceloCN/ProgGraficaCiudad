package com.graphics;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.lwjgl.glfw.GLFW.*;

 
public class Vehiculo extends AppCiudad {
	
	// 5. Trafico autonomo: se hacen protected para que VehiculoAutonomo pueda acceder.
	protected float autoX = -10;
	protected float autoZ = 30;
	protected float angulo = 0;
	protected float velocidad = 0;
	protected boolean cameraArea = false;
	// 2. Iluminacion: nivel de los faros. 0=apagado, 1=bajo, 2=medio, 3=alto.
	protected int nivelFaros = 3;
	// 3. Minimapa: destino activo (punto de entrega). Se reinicia con R.
	protected float destinoX = 30.0f;
	protected float destinoZ = -30.0f;
	// 5. Trafico autonomo: radio ajustado al tamano real del auto para no chocar con aceras.
	protected static final float RADIO_AUTO = 1.5f;
	// 5. Trafico autonomo: distancia de colision entre vehiculos (calles mas amplias visualmente).
	protected static final float DISTANCIA_COLISION_VEHICULO = 3.0f;

	// 6. Chata: remolque enganchado detras del vehiculo del jugador.
	protected static final float ENGANCHE_Z = 1.45f;
	protected static final float DISTANCIA_CHATA = 1.75f;
	// 6. Chata: limite de articulacion; si se supera, la chata tocaria el auto al reversar.
	protected static final float MAX_ARTICULACION = 0.82f;
	protected boolean tieneChata = true;
	protected float chataX = autoX;
	protected float chataZ = autoZ + ENGANCHE_Z + DISTANCIA_CHATA;
	protected float anguloChata = 0;

	// 5. Trafico autonomo: lista de vehiculos autonomos gestionados por este Vehiculo principal.
	private final List<VehiculoAutonomo> trafico = new ArrayList<>();
	
	//   teclado
	@Override
	protected void tecla (int key) {
		super.tecla(key);
		if (key == GLFW_KEY_C) {
			cameraArea = !cameraArea;
		}

		if (key == GLFW_KEY_R) {
			reiniciar();
		}

		// 2. Iluminacion: tecla F cicla los niveles de los faros: OFF -> Bajo -> Medio -> Alto -> OFF.
		if (key == GLFW_KEY_F) {
			nivelFaros = (nivelFaros + 1) % 4;
		}
	}
	
	private void reiniciar() {
		autoX = -10;
		autoZ = 30;
		angulo = 0;
		velocidad = 0;
		nivelFaros = 3;
		destinoX = 30.0f;
		destinoZ = -30.0f;
		chataX = autoX;
		chataZ = autoZ + ENGANCHE_Z + DISTANCIA_CHATA;
		anguloChata = angulo;
		// 5. Trafico autonomo: reiniciar posiciones de los autos autonomos.
		reiniciarTrafico();
	}

	// 5. Trafico autonomo: inicializa los 10 vehiculos autonomos en calles validas.
	@Override
	protected void iniciar() {
		super.iniciar();
		crearTraficoAutonomo();
	}

	private void crearTraficoAutonomo() {
		trafico.clear();
		// 5. Trafico autonomo: posiciones iniciales en celdas de CALLE del mapa 11x11.
		// Las calles estan en indices pares: centro(0)=-50, centro(2)=-30, centro(4)=-10,
		// centro(6)=10, centro(8)=30, centro(10)=50.
		float[][] inicios = {
				{ -50, -30, (float) Math.PI },        // col 0, fila 2, hacia +Z
				{ -30, -50, (float) -Math.PI / 2 },   // col 2, fila 0, hacia +X
				{ -30, -10, 0.0f },                   // col 2, fila 4, hacia -Z
				{ -10, -30, (float) Math.PI / 2 },    // col 4, fila 2, hacia -X
				{ 10, -50, (float) -Math.PI / 2 },    // col 6, fila 0, hacia +X
				{ 30, -30, (float) Math.PI },         // col 8, fila 2, hacia +Z
				{ -50, 10, 0.0f },                    // col 0, fila 6, hacia -Z
				{ -30, 30, (float) -Math.PI / 2 },    // col 2, fila 8, hacia +X
				{ 10, 30, (float) Math.PI / 2 },      // col 6, fila 8, hacia -X
				{ 50, 10, 0.0f }                      // col 10, fila 6, hacia -Z
		};
		for (int i = 0; i < inicios.length; i++) {
			VehiculoAutonomo v = new VehiculoAutonomo(
					inicios[i][0], inicios[i][1], inicios[i][2], i % 3, trafico, this);
			// 5. Trafico autonomo: compartir recursos OpenGL del Vehiculo principal.
			v.programa = this.programa;
			v.vao = this.vao;
			v.uniforms = this.uniforms;
			v.ventana = this.ventana;
			trafico.add(v);
		}
	}

	private void reiniciarTrafico() {
		trafico.clear();
		crearTraficoAutonomo();
	}

	// conduccion
	@Override
	protected void actualizar(float deltaTime) {
		float acelerador = 0;
		if (pulsada(GLFW_KEY_W) || pulsada(GLFW_KEY_UP)) {
			acelerador +=1;
		}
		if (pulsada(GLFW_KEY_S) || pulsada(GLFW_KEY_DOWN)) {
			acelerador -=1;
		}
		velocidad += acelerador * 21 * deltaTime;
		float resistencia = 0.7f;
		if (pulsada(GLFW_KEY_SPACE)) {
			resistencia = 7;
		}
		float factorFrenado = (float)Math.exp(-resistencia * deltaTime);
		velocidad *= factorFrenado;
		velocidad = Math.max(-6, Math.min(30, velocidad));//modifica la velocidad
		float direccion = 0;
		if (pulsada(GLFW_KEY_A) || pulsada(GLFW_KEY_LEFT)) {
			direccion +=1;
		}
		if (pulsada(GLFW_KEY_D) || pulsada(GLFW_KEY_RIGHT)) {
			direccion -=1;
		}
		angulo += direccion * velocidad * 0.11f * deltaTime;
		float frenteX = -(float)Math.sin(angulo);
		float frenteZ = -(float)Math.cos(angulo);
		float siguienteX = autoX + frenteX * velocidad * deltaTime;
		float siguienteZ = autoZ + frenteZ * velocidad * deltaTime;
		// 5. Trafico autonomo: el vehiculo del usuario colisiona con edificios/plazas
		// y con los vehiculos autonomos (no puede atravesarlos). Puede retroceder y
		// reintentar el avance.
		if (puedeCircular(siguienteX, siguienteZ) && !colisionConTrafico(siguienteX, siguienteZ)
				&& !colisionChata(siguienteX, siguienteZ)) {
			autoX = siguienteX;
			autoZ = siguienteZ;
		}else {
			velocidad = 0;
		}
		actualizarChata();
		actualizarTitulo();

		// 5. Trafico autonomo: actualizar todos los vehiculos autonomos.
		for (VehiculoAutonomo v : trafico) {
			v.actualizar(deltaTime);
		}
	}

	// 6. Chata: el punto de enganche sigue la fisica del auto y la chata se
	// articula respecto a el como un remolque real.
	private void actualizarChata() {
		if (!tieneChata) {
			return;
		}
		float engancheX = autoX + (float) Math.sin(angulo) * ENGANCHE_Z;
		float engancheZ = autoZ + (float) Math.cos(angulo) * ENGANCHE_Z;
		float direccionX = chataX - engancheX;
		float direccionZ = chataZ - engancheZ;

		if (direccionX * direccionX + direccionZ * direccionZ > 1e-6f) {
			anguloChata = (float) Math.atan2(direccionX, direccionZ);
		}
		chataX = engancheX + (float) Math.sin(anguloChata) * DISTANCIA_CHATA;
		chataZ = engancheZ + (float) Math.cos(anguloChata) * DISTANCIA_CHATA;
	}

	// 6. Chata: simula la posicion de la chata si el auto se moviera a (siguienteX, siguienteZ).
	// Devuelve true si la chata chocaria con el mapa, con un vehiculo autonomo o con el
	// propio auto al pasarse el limite de articulacion en reversa.
	private boolean colisionChata(float siguienteX, float siguienteZ) {
		if (!tieneChata) {
			return false;
		}
		float engancheX = siguienteX + (float) Math.sin(angulo) * ENGANCHE_Z;
		float engancheZ = siguienteZ + (float) Math.cos(angulo) * ENGANCHE_Z;
		float direccionX = chataX - engancheX;
		float direccionZ = chataZ - engancheZ;
		float anguloNuevo = anguloChata;
		if (direccionX * direccionX + direccionZ * direccionZ > 1e-6f) {
			anguloNuevo = (float) Math.atan2(direccionX, direccionZ);
		}
		float articulacion = anguloNuevo - angulo;
		while (articulacion > Math.PI) {
			articulacion -= 2.0f * (float) Math.PI;
		}
		while (articulacion < -Math.PI) {
			articulacion += 2.0f * (float) Math.PI;
		}
		if (Math.abs(articulacion) > MAX_ARTICULACION) {
			return true;
		}
		float proximaX = engancheX + (float) Math.sin(anguloNuevo) * DISTANCIA_CHATA;
		float proximaZ = engancheZ + (float) Math.cos(anguloNuevo) * DISTANCIA_CHATA;
		if (!puedeCircular(proximaX, proximaZ)) {
			return true;
		}
		for (VehiculoAutonomo v : trafico) {
			float dx = v.autoX - proximaX;
			float dz = v.autoZ - proximaZ;
			if (dx * dx + dz * dz < DISTANCIA_COLISION_VEHICULO * DISTANCIA_COLISION_VEHICULO) {
				return true;
			}
		}
		return false;
	}

	protected void actualizarTitulo() {
		int kilometrosPorHora = Math.round(Math.abs(velocidad) * 3.6f);
		String[] etiquetasFaros = { "OFF", "Bajo", "Medio", "Alto" };
		String titulo = getClass().getSimpleName();
		titulo += " | WASD/Flechas : conducir | Espacio: freno";
		titulo += " | C: camara | R: reiniciar |";
		titulo += " N: " + (modoNocturno ? "noche" : "dia");
		titulo += " | F: faros " + etiquetasFaros[nivelFaros];
		titulo += " | M: minimapa " + (mostrarMinimapa ? "ON" : "OFF");
		titulo += " | " + kilometrosPorHora + " km/h";
		titulo += estadosExtras();
		glfwSetWindowTitle(ventana, titulo);
	}

	protected String estadosExtras() {
		return "";
	}
	
	// 2. Iluminacion / 5. Trafico autonomo: envia los faros del jugador y de todos los autos autonomos.
	@Override
	protected void enviarFarosVehiculo() {
		// uFarosIntensidad: 0=apagado, 0.33=bajo, 0.66=medio, 1.0=alto.
		decimal("uFarosIntensidad", nivelFaros / 3.0f);
		decimal("uFarosCorte", 0.92f); // ~23 grados de apertura del foco

		int idx = 0;
		// Faros del jugador (indices 0 y 1).
		idx = enviarParDeFaros(idx, autoX, autoZ, angulo);

		// Faros de cada vehiculo autonomo.
		for (VehiculoAutonomo v : trafico) {
			idx = enviarParDeFaros(idx, v.autoX, v.autoZ, v.angulo);
		}

		entero("uNumFaros", idx);
	}

	// 5. Trafico autonomo: envia dos faros (izquierdo y derecho) de un vehiculo.
	private int enviarParDeFaros(int idx, float x, float z, float ang) {
		float coseno = (float) Math.cos(ang);
		float seno = (float) Math.sin(ang);
		float frenteX = -(float) Math.sin(ang);
		float frenteZ = -(float) Math.cos(ang);

		float[][] offsets = { { -0.55f, 1.00f, -1.60f }, { 0.55f, 1.00f, -1.60f } };
		for (int i = 0; i < 2; i++) {
			float lx = offsets[i][0];
			float ly = offsets[i][1];
			float lz = offsets[i][2];
			float mundoX = x + coseno * lx + seno * lz;
			float mundoZ = z - seno * lx + coseno * lz;
			vector("uFarosPos[" + idx + "]", mundoX, ly, mundoZ);
			vector("uFarosDir[" + idx + "]", frenteX, -0.08f, frenteZ);
			vector("uFarosColor[" + idx + "]", 1.00f, 0.95f, 0.75f);
			idx++;
		}
		return idx;
	}

	// 3. Minimapa: dibuja el marcador circular del auto (rojo) y el destino (verde).
	@Override
	protected void dibujarMarcadoresMinimapa() {
		// Punto rojo en la posicion actual del vehiculo.
		caja(autoX, 0.5f, autoZ, 1.6f, 0.4f, 1.6f, 1.0f, 0.1f, 0.1f);
		// Pequeno indicador amarillo delante del auto para mostrar orientacion.
		float frenteX = autoX - (float) Math.sin(angulo) * 2.2f;
		float frenteZ = autoZ - (float) Math.cos(angulo) * 2.2f;
		caja(frenteX, 0.5f, frenteZ, 0.7f, 0.4f, 0.7f, 1.0f, 0.9f, 0.0f);
		// Destino de entrega en verde.
		caja(destinoX, 0.5f, destinoZ, 1.6f, 0.4f, 1.6f, 0.0f, 1.0f, 0.2f);
	}

	// 5. Trafico autonomo: detecta si la posicion dada colisionaria con algun vehiculo autonomo.
	protected boolean colisionConTrafico(float x, float z) {
		for (VehiculoAutonomo v : trafico) {
			float dx = v.autoX - x;
			float dz = v.autoZ - z;
			if (dx * dx + dz * dz < DISTANCIA_COLISION_VEHICULO * DISTANCIA_COLISION_VEHICULO) {
				return true;
			}
		}
		return false;
	}

	// colisiones con la ciudad
	protected boolean puedeCircular(float x, float z) {
		float limitePermitido = LIMITE - RADIO_AUTO;
		if (Math.abs(x)> limitePermitido || Math.abs(z)> limitePermitido) {
			return false;
		}
		for (int fila = 0; fila < MAPA.length; fila++) {
			for (int columna = 0; columna < MAPA[fila].length; columna++) {
				int tipo = MAPA[fila][columna];
				if (tipo == 0) {
					continue;
				}
				float centroX = centro(columna);
				float centroZ = centro(fila);
				// El edificio real mide 7x7 y el parque (base verde) 9x9.
				// Se colisiona contra un rectangulo ligeramente menor para dejar
				// margen en la acera y no frenar al vehiculo.
				float mitad = (tipo == 2) ? 4.2f : 3.4f;
				float cercaX = Math.max(centroX - mitad, Math.min(x, centroX + mitad));
				float cercaZ = Math.max(centroZ - mitad, Math.min(z, centroZ + mitad));
				float distanciaX = x - cercaX;
				float distanciaZ = z - cercaZ;
				float distanciaCuadrada = distanciaX * distanciaX + distanciaZ * distanciaZ;
				if (distanciaCuadrada < (RADIO_AUTO * RADIO_AUTO)) {
					return false;
				}
			}
		}
		return true;
	}	
	
	@Override
	protected void configurarCamara() {
		if (cameraArea) {
			super.configurarCamara();
			return;
		}
		float camaraX = autoX + (float) Math.sin(angulo) * 12;
		float camaraZ = autoZ + (float) Math.cos(angulo) * 12;
		
		vector("uOjo", camaraX, 9, camaraZ);
		vector("uObjetivo", autoX, 0.8f, autoZ);
		decimal("uAspecto", (float) ancho/alto);
		
	}
	
	// dibujo del auto
	@Override
	protected void escena() {
		super.escena();
		dibujarAuto();
		// 5. Trafico autonomo: dibujar todos los vehiculos autonomos.
		for (VehiculoAutonomo v : trafico) {
			v.dibujarAuto();
		}
	}
	
	protected void dibujarAuto() {
		pieza(0, 0.65f, 0, 1.65f, 0.55f, 2.6f, 0.95f, 0.24f, 0.12f);//carroceria
		pieza(0, 1.12f, 0.12f, 1.3f, 0.55f, 1.2f, 0.22f, 0.65f, 0.78f); //cabina
		float[] ladosRueda = {-0.88f, 0.88f};
		float[] ejesRueda = { -0.82f, 0.82f};
		
		for (float x : ladosRueda) {
			for ( float z : ejesRueda) {
				pieza(x, 0.38f, z, 0.24f, 0.58f, 0.6f, 0.055f, 0.65f, 0.08f);
			}
		}
		
		float [] ladosFaros = {-0.55f, 0.55f};
		// 2. Iluminacion: los faros delanteros se dibujan con emision para que brillen.
		emision(0.6f);
		for (float x : ladosFaros) {
			pieza(x, 0.68f, -1.32f, 0.38f, 0.2f, 0.07f, 1, 0.95f, 0.65f);
		}
		emision(0.0f);
		for (float x : ladosFaros) {
			pieza(x, 0.68f, 1.32f, 0.35f, 0.2f, 0.07f, 1, 0.05f, 0.05f);
		}
		dibujarChata();
	}

	// 6. Chata: plataforma plana con baranda, varas de tiro y ruedas.
	// Se dibuja con piezaChata(), por lo que sigue la posicion y el angulo articulado.
	private void dibujarChata() {
		// Chasis y plataforma de madera.
		piezaChata(0, 0.46f, 0, 1.75f, 0.10f, 2.45f, 0.28f, 0.28f, 0.32f);
		piezaChata(0, 0.52f, 0, 1.70f, 0.12f, 2.40f, 0.50f, 0.34f, 0.20f);
		// Barandas laterales, frontal y trasera.
		piezaChata(-0.81f, 0.64f, 0, 0.08f, 0.20f, 2.40f, 0.45f, 0.46f, 0.50f);
		piezaChata(0.81f, 0.64f, 0, 0.08f, 0.20f, 2.40f, 0.45f, 0.46f, 0.50f);
		piezaChata(0, 0.64f, -1.16f, 1.70f, 0.20f, 0.08f, 0.45f, 0.46f, 0.50f);
		piezaChata(0, 0.64f, 1.16f, 1.70f, 0.20f, 0.08f, 0.45f, 0.46f, 0.50f);
		// Varilla de tiro y enganche (conectan con la parte trasera del auto).
		piezaChata(0, 0.48f, -1.47f, 0.10f, 0.10f, 0.55f, 0.30f, 0.30f, 0.34f);
		piezaChata(0, 0.50f, -1.75f, 0.18f, 0.14f, 0.14f, 0.35f, 0.35f, 0.38f);
		// Ruedas (dos ejes).
		float[] ladosRueda = { -0.85f, 0.85f };
		float[] ejesRueda = { 0.10f, 0.90f };
		for (float x : ladosRueda) {
			for (float z : ejesRueda) {
				piezaChata(x, 0.36f, z, 0.26f, 0.55f, 0.55f, 0.06f, 0.06f, 0.08f);
			}
		}
		// Luces rojas traseras.
		piezaChata(-0.62f, 0.62f, 1.22f, 0.18f, 0.10f, 0.04f, 1.0f, 0.05f, 0.05f);
		piezaChata(0.62f, 0.62f, 1.22f, 0.18f, 0.10f, 0.04f, 1.0f, 0.05f, 0.05f);
	}
	
	protected void pieza(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b) {
		piezaEn(autoX, autoZ, angulo, x, y, z, sx, sy, sz, r, g, b);
	}

	protected void piezaEn(float baseX, float baseZ, float baseAngulo, float x, float y, float z,
			float sx, float sy, float sz, float r, float g, float b) {
		float coseno = (float) Math.cos(baseAngulo);
		float seno = (float) Math.sin(baseAngulo);
		float mundoX = baseX + coseno * x + seno * z;
		float mundoZ = baseZ - seno * x + coseno * z;
		cajaGirada(mundoX, y, mundoZ, sx, sy, sz, r, g, b, baseAngulo);
	}

	private void piezaChata(float x, float y, float z, float sx, float sy, float sz, float r, float g, float b) {
		piezaEn(chataX, chataZ, anguloChata, x, y, z, sx, sy, sz, r, g, b);
	}
	
	public static void main(String[] args) {
        new Vehiculo().run();
    }
}
