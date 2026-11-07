// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import { createHash } from "node:crypto"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import { LinuxWorkRequestCustody } from "../src/connected-tools.js"
import { prepareNativePeerProofs } from "../src/native-bootstrap-secrets.js"

test("Linux custody requires initialization, retains originals and checks links/permissions on reopen", async () => {
  const configured = process.env.WRITHELM_NATIVE_TEST_ROOT
  assert.ok(configured)
  await fs.mkdir(configured, { recursive: true })
  const area = await fs.mkdtemp(path.join(configured, "native-custody-"))
  const work = path.join(area, "work"), root = path.join(area, "private")
  await fs.mkdir(work)
  const custody = new LinuxNativeCustody(root, [work])
  await assert.rejects(custody.read("credential", "key"))
  await assert.rejects(custody.retain("credential", "key", "original"))
  await custody.initialize()
  assert.equal(await custody.retain("credential", "key", "original"), true)
  assert.equal(await custody.retain("credential", "key", "original"), false)
  await assert.rejects(custody.retain("credential", "key", "changed"))
  const reopened = new LinuxNativeCustody(root, [work])
  await reopened.initialize()
  assert.equal(await reopened.read("credential", "key"), "original")
  const file = path.join(root, `credential-${createHash("sha256").update("key").digest("hex")}.json`)
  await fs.chmod(file, 0o644)
  await assert.rejects(reopened.read("credential", "key"))
  await fs.chmod(file, 0o600)
  await fs.link(file, path.join(area, "linked-secret"))
  await assert.rejects(reopened.read("credential", "key"))
  await assert.rejects(new LinuxNativeCustody(path.join(work, "private"), [work]).initialize())
  const nestedWork = path.join(root, "nested-work")
  await fs.mkdir(nestedWork)
  await assert.rejects(new LinuxNativeCustody(root, [nestedWork]).initialize())
  await assert.rejects(custody.requireOutside(nestedWork))
  const target = path.join(area, "target"), linked = path.join(area, "linked")
  await fs.mkdir(target)
  await fs.symlink(target, linked)
  await assert.rejects(new LinuxNativeCustody(path.join(linked, "must-not-create"), [work]).initialize())
  await assert.rejects(fs.stat(path.join(target, "must-not-create")), { code: "ENOENT" })
  const unsafe = path.join(area, "unsafe")
  await fs.mkdir(unsafe, { mode: 0o777 }); await fs.chmod(unsafe, 0o777)
  await assert.rejects(new LinuxNativeCustody(path.join(unsafe, "must-not-create"), [work]).initialize())
  await assert.rejects(fs.stat(path.join(unsafe, "must-not-create")), { code: "ENOENT" })
  const legacy = path.join(area, "legacy-requests")
  await fs.mkdir(legacy, { mode: 0o700 })
  await fs.writeFile(path.join(legacy, `${"a".repeat(64)}-00000000-0000-0000-0000-000000000001.json`), "original", { mode: 0o600 })
  await assert.rejects(new LinuxWorkRequestCustody(legacy, [work]).retain("binding",
    "00000000-0000-0000-0000-000000000001", "different"), /unrecognized or unresolved/)
  assert.equal((await fs.readdir(legacy)).length, 1)
  const proofs = new LinuxNativeCustody(path.join(area, "proofs"), [work]); await proofs.initialize()
  const original = await prepareNativePeerProofs(proofs), restored = await prepareNativePeerProofs(proofs)
  assert.deepEqual(original.serviceProof, restored.serviceProof)
  assert.deepEqual(original.transportProof, restored.transportProof)
  assert.notDeepEqual(original.serviceProof, original.transportProof)
  const conflict = Buffer.from(original.serviceProof); conflict[0] = conflict[0]! ^ 1
  await assert.rejects(prepareNativePeerProofs(proofs, { serviceProof: conflict, transportProof: original.transportProof }))
  original.serviceProof.fill(0); original.transportProof.fill(0); restored.serviceProof.fill(0); restored.transportProof.fill(0); conflict.fill(0)
})
