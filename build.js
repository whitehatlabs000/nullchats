const fs = require("fs");
const path = require("path");
const { minify } = require("terser");
const CleanCSS = require("clean-css");

const SRC = "src/main/webapp";
const DEST = "target/minified-assets";

function ensureDir(dir) {
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

function cleanDir(dir) {
    if (fs.existsSync(dir)) {
        fs.rmSync(dir, { recursive: true, force: true });
    }
}

async function processFile(srcPath, destPath) {
    const ext = path.extname(srcPath);

    try {

        if (ext === ".css" && srcPath.match(/[\\/]css[\\/]/)) {
            ensureDir(path.dirname(destPath));

            if (srcPath.endsWith(".min.css")) {
                fs.copyFileSync(srcPath, destPath);
            }

            else {
                const input = fs.readFileSync(srcPath, "utf8");
                const output = new CleanCSS().minify(input);
                if (output.errors.length) return console.error("CSS ERROR:", output.errors);
                fs.writeFileSync(destPath, output.styles);
            }
        }

        else if (ext === ".js" && srcPath.match(/[\\/]scripts[\\/]/)) {
            const input = fs.readFileSync(srcPath, "utf8");
            const result = await minify(input, {
                compress: true,
                mangle: true
            });

            ensureDir(path.dirname(destPath));
            fs.writeFileSync(destPath, result.code);
        }
        // Los archivos que no cumplan esto (ej. imágenes o JS externos) serán ignorados por Node.
        // ¡Maven se encargará de copiarlos desde src al WAR!
    } catch (err) {
        console.error("ERROR procesando:", srcPath, err.message);
    }
}

async function processDir(srcDir, destDir) {
    const files = fs.readdirSync(srcDir, { withFileTypes: true });

    for (const file of files) {
        const srcPath = path.join(srcDir, file.name);
        const destPath = path.join(destDir, file.name);

        if (file.isDirectory()) {
            await processDir(srcPath, destPath);
        } else {
            await processFile(srcPath, destPath);
        }
    }
}

(async () => {
    console.log("Procesando archivos con Node y Terser...");
    cleanDir(DEST);
    await processDir(SRC, DEST);
    console.log("✔ Archivos minificados listos en", DEST);
})();