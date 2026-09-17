package com.calendarremember.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.calendarremember.datos.ColorEvento

/**
 * La paleta.
 *
 * El neón sobre negro funciona si el color va en la luz, no en el relleno:
 * fondos casi negros, un acento saturado por elemento y el brillo siempre
 * como halo. En cuanto se rellenan superficies grandes de color saturado
 * deja de parecer neón y empieza a cansar la vista.
 */
object Neon {
    val Fondo = Color(0xFF05060A)
    val Superficie = Color(0xFF0A0E18)
    val SuperficieAlta = Color(0xFF111726)
    val Borde = Color(0xFF1B2336)
    val Texto = Color(0xFFE8ECF5)
    val Tenue = Color(0xFF7C869C)

    val Cian = Color(0xFF00E5FF)
    val Magenta = Color(0xFFFF2FD0)
    val Violeta = Color(0xFF9D5CFF)
    val Verde = Color(0xFF39FF88)
    val Ambar = Color(0xFFFFB03A)

    val Rojo = Color(0xFFFF5C7A)

    fun de(color: ColorEvento): Color = when (color) {
        ColorEvento.CIAN -> Cian
        ColorEvento.MAGENTA -> Magenta
        ColorEvento.VIOLETA -> Violeta
        ColorEvento.VERDE -> Verde
        ColorEvento.AMBAR -> Ambar
    }
}

private val esquema = darkColorScheme(
    primary = Neon.Cian,
    onPrimary = Neon.Fondo,
    secondary = Neon.Magenta,
    background = Neon.Fondo,
    onBackground = Neon.Texto,
    surface = Neon.Superficie,
    onSurface = Neon.Texto,
    surfaceVariant = Neon.SuperficieAlta,
    onSurfaceVariant = Neon.Tenue,
    outline = Neon.Borde,
    error = Neon.Rojo,
)

@Composable
fun TemaNebula(
    @Suppress("UNUSED_PARAMETER") oscuro: Boolean = isSystemInDarkTheme(),
    contenido: @Composable () -> Unit,
) {
    // La app es negra siempre: el tema claro no se contempla, es parte de su
    // identidad y de para qué sirve (mirarla de noche sin deslumbrarse).
    MaterialTheme(colorScheme = esquema, content = contenido)
}
