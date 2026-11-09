// SPDX-License-Identifier: AGPL-3.0-only
import * as fs from "node:fs/promises"
import path from "node:path"

export const primaryPermissionRules = [
  ...["shell","edit","subagent","execute","external_directory"].map(action=>({action,resource:"*",effect:"deny" as const})),
]
export const primaryPolicyStatements = [
  {action:"tool.use",resource:"*",effect:"deny"},
  {action:"tool.use",resource:"question:*",effect:"allow"},
  {action:"tool.use",resource:"read:*",effect:"allow"},
  {action:"tool.use",resource:"writhelm_*:*",effect:"allow"},
]
/** Required native fallback policy: failure/unload of the server plugin must not restore ordinary
 * shell/edit/subagent access to the primary project. Does not grant service or worker authority. */
export async function requirePrimaryPolicy(workRoot:string):Promise<void>{
  const file=path.join(workRoot,"opencode.json")
  if(await fs.realpath(file)!==file)throw new Error("Primary policy path is not qualified")
  const value=JSON.parse(await fs.readFile(file,"utf8"))
  if(JSON.stringify(value.policies?.slice(-primaryPolicyStatements.length))!==JSON.stringify(primaryPolicyStatements))
    throw new Error("Native primary fallback policy missing; refuse GUI-only governance")
}
/** Trusted fresh-profile installer only. Existing project configuration is never overwritten or
 * permissively merged; the owner must review that integration separately. No credentials in repo. */
export async function installFreshPrimaryPolicy(workRoot:string):Promise<void>{
  if(process.platform!=="linux")throw new Error("Primary policy installation adapter is qualified for Linux only")
  const file=path.join(workRoot,"opencode.json")
  try {
    await fs.writeFile(file,JSON.stringify({$schema:"https://opencode.ai/config.json",permissions:primaryPermissionRules,
      policies:primaryPolicyStatements},null,2)+"\n",{flag:"wx",mode:0o600})
  }catch(error){if((error as NodeJS.ErrnoException).code!=="EEXIST")throw error}
  await requirePrimaryPolicy(workRoot)
}
