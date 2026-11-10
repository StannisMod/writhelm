// SPDX-License-Identifier: AGPL-3.0-only
// Pure kernel and actual Linux filesystem primitive. Admission/retention ports are explicit mocks;
// no actual service authority, model, host lifecycle or process-fault qualification.
import test from "node:test"
import assert from "node:assert/strict"
import { createHash } from "node:crypto"
import { prepareExactTextCorrection,applyExactTextFile,observeExactTextEffect,retainExactTextEffect,readRetainedExactTextEffect,PendingTextEffect,type ExactTextCorrection,type RetainedTextEffect } from "../src/exact-text-correction.js"
import { LinuxNativeCustody } from "../src/native-runtime-custody.js"
import * as fs from "node:fs/promises"
import path from "node:path"
import os from "node:os"

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

async function fileFixture(run:(root:string,input:ExactTextCorrection)=>Promise<void>){
  const root=await fs.mkdtemp(path.join(os.tmpdir(),"writhelm-exact-file-"))
  try{
    await fs.mkdir(path.join(root,"src"))
    await fs.writeFile(path.join(root,"src/counter.ts"),"old\n",{mode:0o644})
    await run(root,{path:"src/counter.ts",mode:"100644",beforeText:"old\n",beforeDigest:hash("old\n"),patch:header+"@@ -1 +1 @@\n-old\n+new\n"})
  }finally{await fs.rm(root,{recursive:true,force:true})}
}
const operation="00000000-0000-0000-0000-000000000001"
test("Linux primitive retains exact witnesses before writing and rechecks admission without a model",{skip:process.platform!=="linux"},async()=>{
  await fileFixture(async(root,input)=>{
    const target=path.join(root,input.path),original=await fs.stat(target)
    let admissions=0,retained:RetainedTextEffect|undefined
    const result=await applyExactTextFile(root,input,operation,{
      admit:async intent=>{
        admissions++;assert.equal(intent.root,root);assert.equal(intent.operationId,operation)
        assert.equal(intent.patchDigest,hash(input.patch));assert.equal(intent.correction.beforeDigest,input.beforeDigest)
        assert.equal(intent.correction.afterDigest,hash("new\n"));assert.ok(Object.isFrozen(intent))
      },retain:async effect=>{
        assert.equal(await fs.readFile(target,"utf8"),"old\n")
        assert.equal(effect.operationId,operation);assert.equal(effect.before.digest,input.beforeDigest)
        assert.equal(effect.before.file.inode,String(original.ino));assert.equal(effect.beforeText,"old\n")
        assert.equal(effect.patch,input.patch);assert.equal(effect.patchDigest,hash(input.patch))
        assert.equal(await observeExactTextEffect(effect),"before")
        retained=effect
      },
    })
    assert.equal(result,retained);assert.equal(admissions,2)
    assert.equal(await fs.readFile(target,"utf8"),"new\n")
    assert.equal((await fs.stat(target)).ino,original.ino)
    assert.equal(await observeExactTextEffect(result),"after")
    // Lost caller acknowledgement cannot justify blindly applying the old preimage again.
    await assert.rejects(applyExactTextFile(root,input,operation,{admit:async()=>{},retain:async()=>assert.fail("must not retain a replacement effect")}))
    assert.equal(await fs.readFile(target,"utf8"),"new\n")
    await fs.writeFile(target,"other\n")
    assert.equal(await observeExactTextEffect(result),"unknown")
    await fs.writeFile(target,"old\n")
    assert.equal(await observeExactTextEffect(result),"unknown") // restored bytes do not erase effects
  })
})
test("shorter empty executable and BOM CRLF files retain exact bytes and mode",{skip:process.platform!=="linux"},async()=>{
  for(const [before,after,patchBody] of [
    ["old\n","x\n","@@ -1 +1 @@\n-old\n+x\n"],
    ["old\n","","@@ -1 +0,0 @@\n-old\n"],
    ["\ufeffold\r\n","\ufeffnew\r\n","@@ -1 +1 @@\n-\ufeffold\r\n+\ufeffnew\r\n"],
  ])await fileFixture(async(root,input)=>{
    const target=path.join(root,input.path)
    await fs.writeFile(target,before!);await fs.chmod(target,0o755)
    const effect=await applyExactTextFile(root,{...input,mode:"100755",beforeText:before!,beforeDigest:hash(before!),patch:header+patchBody},operation,{admit:async()=>{},retain:async()=>{}})
    assert.equal(await fs.readFile(target,"utf8"),after)
    assert.equal((await fs.stat(target)).mode&0o7777,0o755)
    assert.equal(await observeExactTextEffect(effect),"after")
  })
})
test("retention failure and revocation after retention perform no writes",{skip:process.platform!=="linux"},async()=>{
  for(const reason of ["retention","revocation"])await fileFixture(async(root,input)=>{
    let admitted=0,retained=false
    await assert.rejects(applyExactTextFile(root,input,operation,{
      admit:async()=>{if(++admitted===2&&reason==="revocation")throw new Error("revoked")},
      retain:async()=>{retained=true;if(reason==="retention")throw new Error("lost retention acknowledgement")},
    }),error=>{
      assert.ok(error instanceof PendingTextEffect);assert.equal(error.effect.operationId,operation)
      assert.equal(error.effect.beforeText,"old\n");return true
    })
    assert.ok(retained);assert.equal(await fs.readFile(path.join(root,input.path),"utf8"),"old\n")
  })
})
test("protected custody retains exact operation before writing and never reauthorizes an existing manifest",{skip:process.platform!=="linux"},async()=>{
  const configured=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(configured)
  const area=await fs.mkdtemp(path.join(configured,"text-effect-")),work=path.join(area,"work"),privateRoot=path.join(area,"private")
  await fs.mkdir(work);await fs.mkdir(path.join(work,"src"))
  const target=path.join(work,"src/counter.ts");await fs.writeFile(target,"old\n",{mode:0o644})
  const input:ExactTextCorrection={path:"src/counter.ts",mode:"100644",beforeText:"old\n",beforeDigest:hash("old\n"),patch:header+"@@ -1 +1 @@\n-old\n+new\n"}
  const custody=new LinuxNativeCustody(privateRoot,[work]);await custody.initialize()
  const effect=await applyExactTextFile(work,input,operation,{admit:async()=>{},retain:async e=>{
    assert.equal(await retainExactTextEffect(custody,e),"new")
    assert.equal(await fs.readFile(target,"utf8"),"old\n")
  }})
  assert.equal(await retainExactTextEffect(custody,effect),"existing") // never issue another write from this outcome
  const reopened=new LinuxNativeCustody(privateRoot,[work]);await reopened.initialize()
  assert.equal(await retainExactTextEffect(reopened,effect),"existing")
  assert.ok(await reopened.read("text-effect",operation))
  assert.ok(await reopened.read("text-effect-part",`${operation}:0`))
  assert.equal(await readRetainedExactTextEffect(reopened,operation),JSON.stringify(effect))
  await assert.rejects(retainExactTextEffect(reopened,{...effect,correction:{...effect.correction,afterText:"not the prepared output"}}))
  const chunkFile=path.join(privateRoot,`text-effect-part-${hash(`${operation}:0`)}.json`)
  await fs.writeFile(chunkFile,"corrupt",{mode:0o600})
  await assert.rejects(readRetainedExactTextEffect(reopened,operation))
})
test("a partially retained original remains held instead of completing or dispatching on retry",{skip:process.platform!=="linux"},async()=>{
  const configured=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(configured)
  const area=await fs.mkdtemp(path.join(configured,"text-partial-")),work=path.join(area,"work")
  await fs.mkdir(work);await fs.mkdir(path.join(work,"src"))
  const target=path.join(work,"src/counter.ts");await fs.writeFile(target,"old\n",{mode:0o644})
  const input:ExactTextCorrection={path:"src/counter.ts",mode:"100644",beforeText:"old\n",beforeDigest:hash("old\n"),patch:header+"@@ -1 +1 @@\n-old\n+new\n"}
  const custody=new LinuxNativeCustody(path.join(area,"private"),[work]);await custody.initialize()
  let captured:RetainedTextEffect|undefined
  await assert.rejects(applyExactTextFile(work,input,operation,{admit:async()=>{},retain:async e=>{
    captured=e
    const bytes=Buffer.from(JSON.stringify(e),"utf8")
    assert.equal(await custody.retain("text-effect-part",`${operation}:0`,bytes.toString("base64")),true)
    throw new Error("simulated interruption before manifest")
  }}),PendingTextEffect)
  assert.ok(captured)
  await assert.rejects(retainExactTextEffect(custody,captured))
  assert.equal(await custody.read("text-effect",operation),undefined)
  assert.equal(await readRetainedExactTextEffect(custody,operation),undefined) // partial chunks are not absence proof
  assert.equal(await fs.readFile(target,"utf8"),"old\n")
})
test("protected effect custody round-trips bounded multichunk UTF-8 without weakening entry limits",{skip:process.platform!=="linux"},async()=>{
  const configured=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(configured)
  const area=await fs.mkdtemp(path.join(configured,"text-chunks-")),work=path.join(area,"work")
  await fs.mkdir(work);await fs.mkdir(path.join(work,"src"))
  const before="я".repeat(150000)+"\n",after="x".repeat(300000)+"\n"
  await fs.writeFile(path.join(work,"src/counter.ts"),before,{mode:0o644})
  const input:ExactTextCorrection={path:"src/counter.ts",mode:"100644",beforeText:before,beforeDigest:hash(before),patch:header+`@@ -1 +1 @@\n-${before}+${after}`}
  const custody=new LinuxNativeCustody(path.join(area,"private"),[work]);await custody.initialize()
  const effect=await applyExactTextFile(work,input,operation,{admit:async()=>{},retain:async e=>{assert.equal(await retainExactTextEffect(custody,e),"new")}})
  assert.ok(await custody.read("text-effect-part",`${operation}:1`))
  assert.equal(await readRetainedExactTextEffect(custody,operation),JSON.stringify(effect))
  assert.equal(await fs.readFile(path.join(work,input.path),"utf8"),after)
})
test("invalid operation refuses before admission and async callbacks cannot retarget a captured request",{skip:process.platform!=="linux"},async()=>{
  await fileFixture(async(root,input)=>{
    await assert.rejects(applyExactTextFile(root,input,"not-an-operation",{admit:async()=>assert.fail("invalid identity admitted"),retain:async()=>{}}))
    const mutable={...input}
    const effect=await applyExactTextFile(root,mutable,operation,{admit:async()=>{
      mutable.path="other";mutable.patch="different payload"
    },retain:async()=>{}})
    assert.equal(effect.before.path,input.path);assert.equal(effect.patch,input.patch)
    assert.equal(await fs.readFile(path.join(root,input.path),"utf8"),"new\n")
    assert.equal(await observeExactTextEffect({...effect,patchDigest:"0".repeat(64)}),"unknown")
    assert.equal(await observeExactTextEffect({...effect,correction:{...effect.correction,afterText:"fake"}}),"unknown")
    assert.equal(await observeExactTextEffect({...effect,before:{...effect.before,path:"../other"}}),"unknown")
    // Simulated partial bytes, not a process-fault test or permission to retry/release.
    await fs.writeFile(path.join(root,input.path),"ne")
    assert.equal(await observeExactTextEffect(effect),"unknown")
  })
})
test("changed file or directory identity after retention refuses without overwriting intervening work",{skip:process.platform!=="linux"},async()=>{
  for(const change of ["content","replacement","directory"])await fileFixture(async(root,input)=>{
    const target=path.join(root,input.path)
    await assert.rejects(applyExactTextFile(root,input,operation,{admit:async()=>{},retain:async()=>{
      if(change==="directory"){
        await fs.rename(path.join(root,"src"),path.join(root,"saved"))
        await fs.mkdir(path.join(root,"src"));await fs.writeFile(target,"old\n",{mode:0o644})
      }else if(change==="replacement"){
        await fs.rename(target,target+".saved");await fs.writeFile(target,"old\n",{mode:0o644})
      }else await fs.writeFile(target,"user work\n")
    }}))
    assert.equal(await fs.readFile(target,"utf8"),change==="content"?"user work\n":"old\n")
  })
})
test("symlinks hardlinks special modes and malformed bytes refuse before retention",{skip:process.platform!=="linux"},async()=>{
  for(const kind of ["symlink","directory-symlink","hardlink","mode","utf8"])await fileFixture(async(root,input)=>{
    const target=path.join(root,input.path)
    if(kind==="symlink"){
      await fs.rename(target,target+".saved");await fs.symlink(target+".saved",target)
    }else if(kind==="directory-symlink"){
      await fs.rename(path.join(root,"src"),path.join(root,"saved"));await fs.symlink(path.join(root,"saved"),path.join(root,"src"))
    }else if(kind==="hardlink")await fs.link(target,target+".alias")
    else if(kind==="mode")await fs.chmod(target,0o4644)
    else await fs.writeFile(target,Buffer.from([0xff]))
    await assert.rejects(applyExactTextFile(root,input,operation,{admit:async()=>{},retain:async()=>assert.fail("unsafe input retained")}))
  })
})
