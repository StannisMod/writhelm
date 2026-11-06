// Deterministic own-schema code generation: no runtime eval needed in the native renderer's CSP.
import Ajv from "ajv/dist/2020.js"
import standalone from "ajv/dist/standalone/index.js"
import { readFile, mkdir, writeFile, unlink } from "node:fs/promises"
const ajv = new Ajv({ strict: false, allErrors: false, coerceTypes: false, removeAdditional: false, useDefaults: false, code: { source: true, lines: true } })
await mkdir("src/generated", { recursive: true })
for (const name of ["service", "profile"]) {
  const schema = JSON.parse(await readFile(`src/schemas/${name}.json`, "utf8"))
  const code = standalone(ajv, ajv.compile(schema))
  await writeFile(`src/generated/${name}.cjs`, "// Generated from the pinned Writhelm schema by Ajv 8.17.1.\n" + code)
  await writeFile(`src/generated/${name}.d.cts`, "// Own generated JSON validator, not a host/SDK declaration.\ndeclare function validate(data: unknown): boolean;\nexport = validate;\n")
  for (const old of [`${name}.js`, `${name}.d.ts`]) await unlink(`src/generated/${old}`).catch(e => { if (e.code !== "ENOENT") throw e })
}
