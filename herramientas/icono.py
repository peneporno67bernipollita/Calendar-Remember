# El icono de Nébula: una nebulosa en espiral con una estrella brillante en
# el centro. Mismo ruido que el fondo animado de la app, para que el icono y
# la app se parezcan. Genera las capas del icono adaptativo en todas las
# densidades y una vista previa.
import os, sys
import numpy as np
from PIL import Image, ImageDraw

aqui = os.path.dirname(os.path.abspath(__file__))
raiz = os.path.dirname(aqui)
res = os.path.join(raiz, "app", "src", "main", "res")
T = 432  # xxxhdpi: 108 dp * 4

def fract(x): return x - np.floor(x)

def hash2(px, py):
    p3x = fract(px * .1031); p3y = fract(py * .1031); p3z = fract(px * .1031)
    d = p3x * (p3y + 33.33) + p3y * (p3z + 33.33) + p3z * (p3x + 33.33)
    p3x += d; p3y += d; p3z += d
    return fract((p3x + p3y) * p3z)

def ruido(px, py):
    ix, iy = np.floor(px), np.floor(py)
    fx, fy = px - ix, py - iy
    a = hash2(ix, iy); b = hash2(ix + 1, iy); c = hash2(ix, iy + 1); d = hash2(ix + 1, iy + 1)
    ux, uy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    return (a + (b - a) * ux) + ((c + (d - c) * ux) - (a + (b - a) * ux)) * uy

def fbm(px, py):
    v = np.zeros_like(px); a = .5
    for _ in range(6):
        v += a * ruido(px, py)
        px, py = 1.6 * px - 1.2 * py, 1.2 * px + 1.6 * py
        a *= .5
    return v

def smooth(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0, 1)
    return t * t * (3 - 2 * t)

def nebulosa(n, semilla=(3.1, 1.7)):
    y, x = np.mgrid[0:n, 0:n].astype(np.float64)
    u = (x + .5) / n - .5; v = (y + .5) / n - .5
    r = np.sqrt(u * u + v * v); ang = np.arctan2(v, u)
    # Espiral: cuanto más cerca del centro, más gira.
    giro = 2.2 * np.exp(-r * 5.0)
    ca, sa = np.cos(giro), np.sin(giro)
    su, sv = u * ca - v * sa, u * sa + v * ca
    px, py = su * 3.2 + semilla[0], sv * 3.2 + semilla[1]
    qx, qy = fbm(px, py), fbm(px + 5.2, py + 1.3)
    rx, ry = fbm(px + 3.2 * qx + 1.7, py + 3.2 * qy + 9.2), fbm(px + 3.2 * qx + 8.3, py + 3.2 * qy + 2.8)
    nn = fbm(px + 2.8 * rx, py + 2.8 * ry)
    # Más gas en el centro, que es lo que se ve dentro de la máscara.
    centro = np.exp(-(r / .33) ** 2)
    nn = np.clip(nn * (.55 + .75 * centro), 0, 1.2)

    fondo = np.array([.012, .014, .045]); violeta = np.array([.42, .20, .88])
    magenta = np.array([.95, .15, .65]); cian = np.array([0., .75, 1.])
    col = np.broadcast_to(fondo, (n, n, 3)).copy()
    gas = smooth(.36, .80, nn)[..., None]
    col = col + (violeta * .95 - col) * (gas * .85)
    col = col + (magenta - col) * (smooth(.42, .92, rx) * .9)[..., None] * gas
    col = col + (cian - col) * (smooth(.48, .90, qy) * .8)[..., None] * gas
    col += np.array([1., .8, 1.]) * (np.maximum(nn - .6, 0) ** 2 * 3.5)[..., None]
    col *= (.35 + .65 * smooth(.28, .7, nn))[..., None]
    # Núcleo luminoso.
    col += np.array([.55, .75, 1.]) * (np.exp(-(r / .10) ** 2) * .55)[..., None]
    return np.clip(col, 0, 1)

def estrellas(img, n, cuantas, semilla):
    """Se pintan en una capa aparte y se mezclan: dibujadas encima sin más,
    el alfa sustituye al fondo en vez de mezclarse."""
    rnd = np.random.default_rng(semilla)
    capa = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(capa, "RGBA")
    for _ in range(cuantas):
        x, y = rnd.random() * n, rnd.random() * n
        rr = (.4 + rnd.random() ** 3 * 1.3) * n / 432 * 2.0
        a = int(120 + rnd.random() * 135)
        if rr > 2.2 * n / 432 * 1.6:
            d.ellipse([x - rr * 3, y - rr * 3, x + rr * 3, y + rr * 3], fill=(200, 230, 255, a // 7))
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(255, 255, 255, a))
    return Image.alpha_composite(img, capa)

def destello(n):
    """La estrella del centro: cuatro puntas finas y un halo cian."""
    y, x = np.mgrid[0:n, 0:n].astype(np.float64)
    u = (x + .5) / n - .5; v = (y + .5) / n - .5
    r = np.sqrt(u * u + v * v) + 1e-9
    # Puntas: brillo que cae rápido fuera de los ejes.
    puntas = (np.exp(-np.abs(u) / .006) * np.exp(-np.abs(v) / .075) +
              np.exp(-np.abs(v) / .006) * np.exp(-np.abs(u) / .075))
    # Diagonales más cortas.
    d1 = np.abs(u - v) / 1.414; d2 = np.abs(u + v) / 1.414
    diag = (np.exp(-d1 / .004) + np.exp(-d2 / .004)) * np.exp(-r / .05) * .5
    nucleo = np.exp(-(r / .028) ** 2) * 1.2
    halo = np.exp(-(r / .085) ** 2) * .55
    blanco = np.clip(puntas * .95 + diag + nucleo, 0, 1)
    alfa = np.clip(blanco + halo, 0, 1)
    rgb = np.zeros((n, n, 3))
    # El halo tira a cian; el centro, blanco.
    rgb[..., 0] = np.clip(blanco + halo * .25, 0, 1)
    rgb[..., 1] = np.clip(blanco + halo * .85, 0, 1)
    rgb[..., 2] = np.clip(blanco + halo * 1.0, 0, 1)
    # Premultiplicado a recto: el color donde hay alfa.
    with np.errstate(invalid="ignore", divide="ignore"):
        recto = np.where(alfa[..., None] > 0, np.clip(rgb / np.maximum(alfa[..., None], 1e-6), 0, 1), 0)
    return np.dstack([recto, alfa])

def a_imagen(arr):
    return Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8))

fondo = a_imagen(nebulosa(T)).convert("RGBA")
fondo = estrellas(fondo, T, 70, 5)
frente = a_imagen(destello(T))

densidades = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
for nombre, lado in densidades.items():
    carpeta = os.path.join(res, f"mipmap-{nombre}")
    os.makedirs(carpeta, exist_ok=True)
    fondo.resize((lado, lado), Image.LANCZOS).convert("RGB").save(os.path.join(carpeta, "ic_launcher_fondo.png"), optimize=True)
    frente.resize((lado, lado), Image.LANCZOS).save(os.path.join(carpeta, "ic_launcher_primer.png"), optimize=True)

# Vista previa: como lo recorta un lanzador (círculo del 72/108 central).
vista = Image.alpha_composite(fondo, frente)
m = Image.new("L", (T, T), 0)
ImageDraw.Draw(m).ellipse([T * 18 / 108, T * 18 / 108, T * 90 / 108, T * 90 / 108], fill=255)
circ = Image.new("RGBA", (T, T), (30, 30, 36, 255)); circ.paste(vista, (0, 0), m)
lienzo = Image.new("RGBA", (T * 2 + 20, T), (30, 30, 36, 255))
lienzo.paste(vista, (0, 0)); lienzo.paste(circ, (T + 20, 0))
lienzo.save(os.path.join(aqui, "icono_vista.png"))  # para mirarlo, no va en la app
print("ok")
