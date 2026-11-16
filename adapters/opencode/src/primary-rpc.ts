// SPDX-License-Identifier: AGPL-3.0-only
import { Rpc } from "@opencode/plugin/rpc"

/** Native-authenticated UI/server admission, not a model tool, credential issuer or worker-ready
 * assertion. The server derives ownership/current authority from the actual session binding. */
const input={type:"object",additionalProperties:false,required:["sessionID"],properties:{sessionID:{type:"string",maxLength:132}}} as const
const output={type:"object",additionalProperties:false,required:["kind","role","sessionID","projectId"],properties:{
  kind:{type:"string",enum:["governed-planning"]},role:{type:"string",enum:["controller"]},
  sessionID:{type:"string"},projectId:{type:"string"}}} as const
export const ClientAdmission=Rpc.define({id:"writhelm-client-admission",methods:{admitController:{
  // Native 2.2.5 disables JSON-Schema patterns. The handler applies the exact bounded ID regex.
  input,output,
},createController:{input,output}},events:{}})
