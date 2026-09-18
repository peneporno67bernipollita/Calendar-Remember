package com.calendarremember.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * El fondo: una nebulosa que se mueve despacio, con estrellas que titilan.
 *
 * No es un vídeo ni un GIF: se calcula en cada fotograma. Por eso no hay un
 * bucle que se note al repetirse. El gas deriva por dos caminos circulares
 * de periodos que no encajan entre sí (173 y 241 segundos), así que la
 * imagen no vuelve a ser la misma en horas, y sin saltos.
 *
 * Coste: el gas se pinta a un cuarto de resolución y se amplía (una
 * nebulosa es difusa, no se nota) y a unos 30 fotogramas por segundo, que
 * para algo que se mueve tan despacio sobra. Las estrellas sí van a
 * resolución completa, porque tienen que ser puntos nítidos. Cuando la app
 * no está a la vista, no se pinta nada.
 */
@Composable
fun FondoNebula(modifier: Modifier = Modifier, intensidad: Float = 1f) {
    val tiempo = rememberTiempo()
    Box(modifier.fillMaxSize().drawBehind { drawRect(Neon.Fondo) }) {
        val shader = remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) crearShader() else null
        }
        if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GasConShader(shader, tiempo, intensidad)
        } else {
            GasSencillo(tiempo, intensidad)
        }
        Estrellas(tiempo)
    }
}

/** Segundos desde que se abrió la pantalla, a unos 30 fotogramas por segundo. */
@Composable
private fun rememberTiempo(): State<Float> {
    val t = remember {
        // Cada vez empieza en un punto distinto del recorrido.
        mutableFloatStateOf((System.currentTimeMillis() / 1000 % 900).toFloat())
    }
    LaunchedEffect(Unit) {
        val base = t.floatValue
        val inicio = withFrameNanos { it }
        var ultimo = 0L
        while (true) {
            withFrameNanos { ahora ->
                if (ahora - ultimo >= 33_000_000L) {
                    ultimo = ahora
                    t.floatValue = base + (ahora - inicio) / 1_000_000_000f
                }
            }
        }
    }
    return t
}

// --- Android 13 y siguientes: el gas con un shader ------------------------

/**
 * Ruido fractal deformado dos veces sobre sí mismo ("domain warping"), que
 * es lo que da las volutas de una nebulosa de verdad en vez de manchas.
 * Colores de la app: violeta de base, magenta y cian en los bordes del gas,
 * y un blanco rosado donde es más denso. Los bordes de la pantalla se
 * oscurecen para que el texto siempre se lea.
 */
private const val NEBULOSA_AGSL = """
uniform float2 res;
uniform float t;
uniform float intensidad;

// Hash sin senos y con la parte fraccionaria tomada pronto: con
// coordenadas de cientos, los hash clasicos pierden precision y salen
// bandas cuadradas.
float hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float ruido(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    float2x2 giro = float2x2(1.6, 1.2, -1.2, 1.6);
    for (int i = 0; i < 5; i++) {
        v += a * ruido(p);
        p = giro * p;
        a *= 0.5;
    }
    return v;
}

half4 main(float2 punto) {
    float2 uv = punto / res.y;
    float a1 = t * 6.2831853 / 173.0;
    float a2 = t * 6.2831853 / 241.0;
    float2 d1 = float2(cos(a1), sin(a1)) * 0.6;
    float2 d2 = float2(cos(a2), sin(a2)) * 0.9;
    float2 p = uv * 2.4 + float2(3.1, 1.7);

    float2 q = float2(fbm(p + d1), fbm(p + float2(5.2, 1.3) - d1));
    float2 r = float2(fbm(p + 3.2 * q + d2 + float2(1.7, 9.2)), fbm(p + 3.2 * q - d2 + float2(8.3, 2.8)));
    float n = fbm(p + 2.8 * r);

    float3 fondo = float3(0.010, 0.012, 0.035);
    float3 violeta = float3(0.40, 0.20, 0.85);
    float3 magenta = float3(0.90, 0.14, 0.62);
    float3 cian = float3(0.00, 0.70, 0.95);

    float3 col = fondo;
    float gas = smoothstep(0.38, 0.82, n);
    col = mix(col, violeta * 0.9, gas * 0.75 * intensidad);
    col = mix(col, magenta, smoothstep(0.42, 0.92, r.x) * 0.85 * gas * intensidad);
    col = mix(col, cian, smoothstep(0.48, 0.90, q.y) * 0.75 * gas * intensidad);
    col += float3(1.0, 0.78, 1.0) * pow(max(n - 0.62, 0.0), 2.0) * 3.0 * intensidad;
    // Entre nube y nube, espacio casi negro: ahi descansa la vista y se lee.
    col *= 0.35 + 0.65 * smoothstep(0.30, 0.70, n);

    float borde = 1.0 - smoothstep(0.25, 1.3, length(punto / res - 0.5) * 1.4);
    col *= mix(0.55, 1.0, borde);
    return half4(half3(col), 1.0);
}
"""

private const val REDUCCION = 4f

/** Si el shader no compila en algún móvil raro, se usa el fondo sencillo. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun crearShader(): RuntimeShader? = runCatching { RuntimeShader(NEBULOSA_AGSL) }.getOrNull()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun GasConShader(shader: RuntimeShader, tiempo: State<Float>, intensidad: Float) {
    val pincel = remember(shader) { ShaderBrush(shader) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Se pinta en una capa cuatro veces más pequeña y se amplía al
        // componer: dieciséis veces menos píxeles que calcular.
        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(maxWidth / REDUCCION + 1.dp, maxHeight / REDUCCION + 1.dp)
                .graphicsLayer {
                    scaleX = REDUCCION
                    scaleY = REDUCCION
                    transformOrigin = TransformOrigin(0f, 0f)
                    compositingStrategy = CompositingStrategy.Offscreen
                }
                .drawBehind {
                    shader.setFloatUniform("res", size.width, size.height)
                    shader.setFloatUniform("t", tiempo.value)
                    shader.setFloatUniform("intensidad", intensidad)
                    drawRect(pincel)
                }
        )
    }
}

// --- Android 8 a 12: nubes de color que derivan ---------------------------

/** Sin shaders: cinco nubes difusas que se mueven por curvas lentas. */
@Composable
private fun GasSencillo(tiempo: State<Float>, intensidad: Float) {
    val nubes = remember {
        listOf(
            Nube(Neon.Violeta, 0.55f, 0.30f, 0.70f, 97f, 131f, 0.0f),
            Nube(Neon.Magenta, 0.45f, 0.70f, 0.55f, 113f, 83f, 1.7f),
            Nube(Neon.Cian, 0.35f, 0.45f, 0.50f, 151f, 107f, 3.1f),
            Nube(Neon.Violeta, 0.40f, 0.85f, 0.60f, 127f, 173f, 4.4f),
            Nube(Neon.Magenta, 0.30f, 0.15f, 0.45f, 89f, 139f, 5.6f),
        )
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = tiempo.value
        for (n in nubes) {
            val cx = size.width * (n.x + 0.18f * sin(2 * PI.toFloat() * t / n.periodoX + n.fase))
            val cy = size.height * (n.y + 0.10f * cos(2 * PI.toFloat() * t / n.periodoY + n.fase))
            val radio = size.maxDimension * n.radio
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(n.color.copy(alpha = 0.20f * intensidad), Color.Transparent),
                    center = Offset(cx, cy), radius = radio,
                ),
                radius = radio, center = Offset(cx, cy),
            )
        }
    }
}

private class Nube(
    val color: Color, val x: Float, val y: Float, val radio: Float,
    val periodoX: Float, val periodoY: Float, val fase: Float,
)

// --- Las estrellas --------------------------------------------------------

private class Estrella(
    val x: Float, val y: Float, val radio: Float, val brillo: Float,
    val velocidad: Float, val fase: Float, val color: Color,
)

@Composable
private fun Estrellas(tiempo: State<Float>) {
    val densidad = LocalDensity.current.density
    val estrellas = remember {
        // Siempre las mismas: el cielo no cambia de sitio al volver a la app.
        val azar = Random(1987)
        List(150) {
            val tinte = azar.nextFloat()
            Estrella(
                x = azar.nextFloat(), y = azar.nextFloat(),
                radio = (0.5f + azar.nextFloat() * azar.nextFloat() * 1.4f) * densidad,
                brillo = 0.35f + azar.nextFloat() * 0.65f,
                velocidad = 0.4f + azar.nextFloat() * 1.6f,
                fase = azar.nextFloat() * 6.283f,
                color = when {
                    tinte < 0.12f -> Color(0xFFBFF7FF)
                    tinte < 0.22f -> Color(0xFFFFC8F2)
                    else -> Color.White
                },
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = tiempo.value
        for (e in estrellas) {
            val titileo = 0.55f + 0.45f * sin(t * e.velocidad + e.fase)
            val alfa = (e.brillo * titileo).coerceIn(0f, 1f)
            val centro = Offset(e.x * size.width, e.y * size.height)
            // Un halo tenue alrededor de las más grandes.
            if (e.radio > 1.3f * densidad) {
                drawCircle(e.color.copy(alpha = alfa * 0.18f), radius = e.radio * 3.2f, center = centro)
            }
            drawCircle(e.color.copy(alpha = alfa), radius = e.radio, center = centro)
        }
    }
}
