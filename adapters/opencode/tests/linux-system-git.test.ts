// SPDX-License-Identifier: AGPL-3.0-only
import test from "node:test"
import assert from "node:assert/strict"
import * as fs from "node:fs/promises"
import path from "node:path"
import {execFile} from "node:child_process"
import {promisify} from "node:util"
import {GitCompatibilityError,observeSystemGit} from "../src/linux-system-git.js"
import {LinuxGitWitness} from "../src/linux-git-witness.js"

test("Linux capability cache admits compatible changed builds and refuses missing/incompatible replacements",async()=>{
  const root=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(root)
  const area=await fs.mkdtemp(path.join(root,"system-git-")),file=path.join(area,"git")
  const write=async(version:string,bad=false)=>{
    await fs.writeFile(file,`#!/bin/sh\nif [ "$1" = --version ]; then echo 'git version ${version}'; exit 0; fi\n`+
      (bad?"echo unsupported; exit 1\n":'exec /usr/bin/git "$@"\n'),{mode:0o700})
  }
  // Synthetic launchers test observation invalidation, not qualification of another Git release.
  await write("2.43.0.synthetic")
  assert.equal((await observeSystemGit(file,new AbortController().signal)).version,"git version 2.43.0.synthetic")
  await write("2.56.1.synthetic-build")
  assert.equal((await observeSystemGit(file,new AbortController().signal)).version,"git version 2.56.1.synthetic-build")
  const link=path.join(area,"system-git-link");await fs.symlink(file,link)
  assert.equal((await observeSystemGit(link,new AbortController().signal)).version,"git version 2.56.1.synthetic-build")
  await write("2.56.1.synthetic-build",true)
  await assert.rejects(observeSystemGit(file,new AbortController().signal),(e:unknown)=>
    e instanceof GitCompatibilityError&&e.code==="GIT_INCOMPATIBLE")
  await assert.rejects(observeSystemGit(link,new AbortController().signal),(e:unknown)=>
    e instanceof GitCompatibilityError&&e.code==="GIT_INCOMPATIBLE")
  await assert.rejects(observeSystemGit(path.join(area,"missing"),new AbortController().signal),(e:unknown)=>
    e instanceof GitCompatibilityError&&e.code==="GIT_UNAVAILABLE")
})
test("legacy executable digest does not block a real exact-object Git witness",async()=>{
  const root=process.env.WRITHELM_NATIVE_TEST_ROOT;assert.ok(root)
  const area=await fs.mkdtemp(path.join(root,"legacy-git-witness-")),run=promisify(execFile)
  const git=async(args:string[],input?:string)=>{
    assert.equal(input,undefined)
    return (await run("/usr/bin/git",["-c","core.hooksPath=/dev/null","-c","user.name=Synthetic",
      "-c","user.email=synthetic@invalid","-C",area,...args],{env:{PATH:"/usr/bin:/bin",LC_ALL:"C",
        GIT_CONFIG_NOSYSTEM:"1",GIT_CONFIG_GLOBAL:"/dev/null"}})).stdout.trim()
  }
  await git(["init","--template=","--initial-branch=main"])
  await fs.writeFile(path.join(area,"source.txt"),"Exact synthetic source.")
  await git(["add","source.txt"]);await git(["commit","-m","synthetic"])
  const commit=await git(["rev-parse","HEAD"]),tree=await git(["rev-parse","HEAD^{tree}"])
  const witness=new LinuxGitWitness({path:"/usr/bin/git",sha256:"0".repeat(64)})
  await witness.qualifySource(area,{repositoryId:"00000000-0000-0000-0000-000000000001",
    commit:{algorithm:"sha1",hex:commit},tree:{algorithm:"sha1",hex:tree},
    dirtyContentDigest:null,retainedLocatorId:"00000000-0000-0000-0000-000000000002"},new AbortController().signal)
})
