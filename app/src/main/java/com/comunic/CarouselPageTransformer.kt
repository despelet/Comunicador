package com.comunic

// CarouselPageTransformer.kt
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs


/*
Este código hace lo siguiente:

Recibe la posición de cada página. 0.0 es el centro, -1.0 es la página a la
 izquierda y 1.0 es la de la derecha.

Si la página está en el centro, la deja a su tamaño y opacidad completos (1f).

A medida que la página se aleja del centro, reduce progresivamente
 su escala (hasta un 85% de su tamaño) y su opacidad (hasta un 50%).

Para Aplicar el PageTransformer en CuadroImagen.kt
Finalmente, tenemos que decirle a nuestro ViewPager2
 que use el Transformer que acabamos de crear.

Ve a tu archivo CuadroImagen.kt, dentro de la función
 configurarCarruselPrincipal(), y añade la siguiente línea:
     binding.mainCarousel.setPageTransformer(CarouselPageTransformer())

 */

private const val MIN_SCALE = 0.85f // La escala mínima para las páginas laterales
private const val MIN_ALPHA = 0.5f // La transparencia mínima

class CarouselPageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        page.apply {
            val pageWidth = width
            val pageHeight = height

            when {
                // Página en el centro
                position == 0f -> {
                    scaleX = 1f
                    scaleY = 1f
                    alpha = 1f
                }
                // Páginas a los lados
                else -> {
                    // Modificar la escala
                    val scaleFactor = MIN_SCALE.coerceAtLeast(1 - abs(position))
                    scaleX = scaleFactor
                    scaleY = scaleFactor

                    // Modificar la transparencia
                    alpha = MIN_ALPHA.coerceAtLeast(1 - abs(position))
                }
            }
        }
    }
}