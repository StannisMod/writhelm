// SPDX-License-Identifier: AGPL-3.0-only
// Pure deterministic kernel only: no filesystem writes, model, host/session, process or service.
import test from "node:test"
import assert from "node:assert/strict"
import { createHash } from "node:crypto"
import { prepareExactTextCorrection } from "../src/exact-text-correction.js"

const hash=(text:string)=>createHash("sha256").update(text).digest("hex")
const apply=(beforeText:string,patch:string,path="src/counter.ts")=>prepareExactTextCorrection({path,mode:"100644",beforeText,beforeDigest:hash(beforeText),patch})
const header="--- a/src/counter.ts\n+++ b/src/counter.ts\n"

test("exact positions and bytes produce one immutable prepared text result without rederiving a fix",()=>{
  const before="const score = 0\nkeep\nlast\n"
  const result=apply(before,header+"@@ -1 +1 @@\n-const score = 0\n+const score = 1\n@@ -3 +3 @@\n-last\n+done\n")
  assert.equal(result.afterText,"const score = 1\nkeep\ndone\n")
  assert.equal(result.beforeDigest,hash(before));assert.equal(result.afterDigest,hash(result.afterText))
  assert.equal(result.path,"src/counter.ts");assert.ok(Object.isFrozen(result))
  const gitHeader=`diff --git a/src/counter.ts b/src/counter.ts\nindex ${"0".repeat(40)}..${"1".repeat(40)} 100644\n`
  assert.equal(apply(before,gitHeader+header+"@@ -1 +1 @@\n-const score = 0\n+const score = 1\n").afterText,"const score = 1\nkeep\nlast\n")
  assert.throws(()=>apply(before,gitHeader.replace("100644","100755")+header+"@@ -1 +1 @@\n-const score = 0\n+const score = 1\n"))
})

test("CRLF, BOM, multibyte fragments and missing final newline are preserved, not normalized",()=>{
  const before="\ufeffПривет e\u0301\r\nold"
  const result=apply(before,header+"@@ -1,2 +1,2 @@\n \ufeffПривет e\u0301\r\n-old\n\\ No newline at end of file\n+new\n\\ No newline at end of file\n")
  assert.equal(result.afterText,"\ufeffПривет e\u0301\r\nnew")
  assert.equal(apply("",header+"@@ -0,0 +1 @@\n+x\n").afterText,"x\n")
  assert.equal(apply("x\n",header+"@@ -1 +0,0 @@\n-x\n").afterText,"")
})

test("no fuzzy matching, offset guessing, new-file/rename/binary/mode changes, second file or no-op",()=>{
  const before="a\nb\nc\n"
  for(const patch of [
    header+"@@ -1 +1 @@\n-b\n+B\n", // b exists elsewhere; no searching for a match
    header+"@@ -2 +3 @@\n-b\n+B\n", // new position disagrees with actual delta
    header+"@@ -1,2 +1 @@\n-a\n+A\n", // declared old count mismatch
    header+"@@ -2 +2 @@\n-b\n+B\n@@ -1 +1 @@\n-a\n+A\n", // reversed/overlapping positions
    header+"@@ -1 +1 @@\n a\n", // no change
    "--- /dev/null\n+++ b/src/counter.ts\n@@ -0,0 +1 @@\n+x\n",
    "--- a/src/counter.ts\n+++ b/src/other.ts\n@@ -1 +1 @@\n-a\n+A\n",
    "diff --git a/src/counter.ts b/src/counter.ts\nold mode 100644\nnew mode 100755\n"+header,
    "GIT binary patch\n",
    header+"@@ -1 +1 @@\n-a\n+A\n--- a/other\n+++ b/other\n",
    header+"@@ -1 +1 @@\n-a\n+A", // incomplete patch transport
    header+"@@ -1 +1 @@\n-a\n+A\n\\ No newline at end of file\n", // would join untouched suffix
  ])assert.throws(()=>apply(before,patch),patch)
  assert.throws(()=>prepareExactTextCorrection({path:"src/counter.ts",mode:"100644",beforeText:before,beforeDigest:"0".repeat(64),patch:header+"@@ -1 +1 @@\n-a\n+A\n"}))
})

test("portable path subset rejects aliases/control areas; patch body is never interpreted as commands",()=>{
  for(const path of ["../other","/absolute","a/../../other","a//b",".git/config",".GIT/config","a/NUL.txt","a/COM1","a/file.","C:/other","a\\b","name with spaces","юникод"])
    assert.throws(()=>apply("old\n",`--- a/${path}\n+++ b/${path}\n@@ -1 +1 @@\n-old\n+new\n`,path))
  const result=apply("old\n",header+"@@ -1 +1 @@\n-old\n+$(this is literal text, not a command)\n")
  assert.equal(result.afterText,"$(this is literal text, not a command)\n")
  assert.throws(()=>apply("\ud800",header+"@@ -1 +1 @@\n-x\n+y\n"))
  assert.throws(()=>apply("\n".repeat(131073),header+"@@ -1 +1 @@\n-\n+x\n"))
})
