// SPDX-License-Identifier: AGPL-3.0-only
import type {BoundWorkChannel} from "./connected-tools.js"
import {planningKnowledgeReads} from "./connected-tools.js"
import {ModelReads} from "./model-read.js"
import {revisionSources} from "./model-read-knowledge.js"
import {contextJSON} from "./native-context-codec.js"
import {exactJSON} from "./strict-json.js"
import {nativeUnavailable} from "./native-runtime-client.js"
import {operationSchema,validateModelContract} from "./model-contracts.js"
import {prepareKnowledgeDocument,knowledgeIntentIdentity} from "./knowledge-preparation.js"
type Source={reference:string;clauseId?:string}
type Send=(raw:string)=>Promise<string>
const plain=(v:unknown)=>JSON.parse(contextJSON(v,4*1024*1024))
/** Preserve a source-read refusal instead of misreporting it as runtime/command uncertainty. */
export class ReviewSourceReadHold extends Error{
  constructor(readonly reference:string,readonly code:string){
    super(`Review preparation source ${reference} returned ${code}. No canonical command was submitted by this attempt. Preserve the original intentId; do not create a replacement request.`)
  }
}
/** Friendly authoring prepares scoped service commands; source-closure resolution belongs to submit_review.
 * It has no owner credential, direct canonical writer, approval shortcut or implicit latest lookup. */
export class ModelKnowledge {
  constructor(private readonly reads:ModelReads){}
  async propose(input:any,channel:BoundWorkChannel,sessionID:string,signal:AbortSignal,send:Send){
    validateModelContract(operationSchema("authoring","propose_document"),input)
    const identity=(purpose:string)=>knowledgeIntentIdentity(channel.readBinding!,input.intentId,purpose)
    const prepared=prepareKnowledgeDocument(channel.projectID,input.documentId??identity("document"),identity("revision"),
      input.clauses,input.context??"")
    const raw=contextJSON({formatVersion:1,projectId:channel.projectID,requestKey:identity("propose-command"),operation:"propose_revision",
      payload:{revisionKey:prepared.revisionKey,body:prepared.body,structure:prepared.structure}},2*1024*1024)
    const receipt=await send(raw),result=exactJSON(receipt,nativeUnavailable)
    if(result.kind!=="settled"||result.outcome?.kind!=="success")return {content:receipt}
    // Verify actual immutable service bytes/digests with the ordinary current-authorized read path.
    // No local source cache is promoted into a service reference.
    try{
      const read=await this.reads.read("knowledge",channel,sessionID,{json:contextJSON({formatVersion:1,projectId:channel.projectID,
        operation:"read_revision",subject:{revisionKey:prepared.revisionKey,revisionDigest:prepared.revisionDigest}}),format:"canonical"},
        planningKnowledgeReads,signal)
      const observed=exactJSON(read.content,nativeUnavailable)
      if(observed.kind!=="read"||observed.data?.body!==prepared.body)nativeUnavailable()
      return {content:JSON.stringify({receipt:plain(result),revisionReference:read.metadata?.writhelmReadReference,
        documentId:prepared.revisionKey.document.documentId,revisionId:prepared.revisionKey.revisionId,
        clauses:input.clauses.map((c:any)=>({id:c.id,title:c.title})),
        next:"Use revisionReference plus clauseId in writhelm_request_review items. No manual spans or hashes. This is a proposal, not approval."}),
        metadata:read.metadata}
    }catch{
      return {content:receipt+"\nProposal is canonically settled; reference delivery held. Retry the SAME intentId and identical authored content to recover. Do not create a replacement intent."}
    }
  }
  async review(input:any,channel:BoundWorkChannel,sessionID:string,signal:AbortSignal,send:Send){
    validateModelContract(operationSchema("authoring","request_review"),input)
    if(new Set(input.items.map((item:any)=>item.key)).size!==input.items.length)nativeUnavailable()
    const identity=(purpose:string)=>knowledgeIntentIdentity(channel.readBinding!,input.intentId,purpose)
    const original=await channel.originalReview?.(identity("review-command"))
    if(original!==undefined){
      const command=exactJSON(original,nativeUnavailable)
      if(command.projectId!==channel.projectID||command.requestKey!==identity("review-command")||
        command.payload?.requestId!==identity("approval-request")||
        !["request_approval","submit_review"].includes(command.operation))nativeUnavailable()
      // Old operations stay on their byte-identical legacy preparation/recovery route.
      if(command.operation==="request_approval")return this.legacyReview(input,channel,sessionID,signal,send)
    }
    const selectors=new Map<string,Promise<any>>()
    const resolve=async(source:Source)=>{
      if(!selectors.has(source.reference))selectors.set(source.reference,this.reads.reviewSelector(channel,sessionID,source.reference,signal))
      const request=await selectors.get(source.reference)!
      if(request.operation==="read_revision")return {kind:"revision",...request.subject,clauseId:source.clauseId??null}
      const ref=request.subject
      if(source.clauseId!==undefined&&ref.clauseId!==source.clauseId)nativeUnavailable()
      return ref
    }
    const items=[]
    for(const item of input.items)items.push({id:identity("item:"+item.key),target:await resolve(item.target),
      before:item.before===null?null:await resolve(item.before)})
    const draft:any={items,question:input.question,scope:input.scope,rationale:input.rationale,
      consequences:input.consequences,alternatives:input.alternatives,uncertainties:input.uncertainties,
      contexts:await Promise.all(input.contexts.map(resolve)),dependencies:await Promise.all(input.dependencies.map(resolve)),
      informationGaps:input.informationGaps}
    if(input.presentations?.length)draft.presentations=input.presentations.map((presentation:any)=>({
      language:presentation.language,sourceLanguage:presentation.sourceLanguage,items:presentation.items.map((item:any)=>{
        if(!input.items.some((candidate:any)=>candidate.key===item.itemKey))nativeUnavailable()
        return {itemId:identity("item:"+item.itemKey),title:item.title,text:item.text}
      })
    }))
    if(input.followup)draft.followup={requestId:input.followup.requestId,items:input.followup.items.map((change:any)=>{
      if(change.itemKey!==null&&!input.items.some((item:any)=>item.key===change.itemKey))nativeUnavailable()
      return {previousItemId:change.previousItemId,itemId:change.itemKey===null?null:identity("item:"+change.itemKey),
        disposition:change.disposition,explanation:change.explanation}
    })}
    const requestId=identity("approval-request")
    const command=contextJSON({formatVersion:1,projectId:channel.projectID,requestKey:identity("review-command"),
      operation:"submit_review",payload:{requestId,draft}},2*1024*1024)
    if(original!==undefined&&original!==command)throw new Error("Original review input conflict; preserve the original command and intent.")
    return {content:await send(command),metadata:{writhelmRequestId:requestId}}
  }
  private async legacyReview(input:any,channel:BoundWorkChannel,sessionID:string,signal:AbortSignal,send:Send){
    validateModelContract(operationSchema("authoring","request_review"),input)
    if(new Set(input.items.map((item:any)=>item.key)).size!==input.items.length)nativeUnavailable()
    const identity=(purpose:string)=>knowledgeIntentIdentity(channel.readBinding!,input.intentId,purpose)
    const sources=new Map<string,Promise<any>>()
    const resolve=async(source:Source)=>{
      if(!sources.has(source.reference))sources.set(source.reference,this.reads.read("knowledge",channel,sessionID,
        {reference:source.reference,format:"canonical"},planningKnowledgeReads,signal).then(result=>exactJSON(result.content,nativeUnavailable)))
      const read=await sources.get(source.reference)!
      if(read.kind!=="read"){
        const code=typeof read.code==="string"&&/^[A-Z][A-Z0-9_]{0,63}$/.test(read.code)?read.code:"INVALID_READ_RESPONSE"
        throw new ReviewSourceReadHold(source.reference,code)
      }
      if(read.operation==="read_revision"){
        const candidates=revisionSources(read.data).map(s=>s.reference)
        const ref=candidates.find((r:any)=>source.clauseId===undefined?r.kind==="context":r.kind==="clause"&&r.clauseId===source.clauseId)
        if(!ref)nativeUnavailable()
        return ref
      }
      if(["read_clause","read_context","read_approval_trace"].includes(read.operation)){
        const ref=read.data.reference
        if(source.clauseId!==undefined&&ref.clauseId!==source.clauseId)nativeUnavailable()
        return ref
      }
      nativeUnavailable()
    }
    const items=[]
    for(const item of input.items)items.push({id:identity("item:"+item.key),target:await resolve(item.target),
      before:item.before===null?null:await resolve(item.before)})
    const contexts=[]
    for(const source of input.contexts){const ref=await resolve(source);if(ref.kind!=="context")nativeUnavailable();contexts.push(ref)}
    const dependencies=[]
    for(const source of input.dependencies)dependencies.push(await resolve(source))
    const packet:any={items,question:input.question,scope:input.scope,rationale:input.rationale,consequences:input.consequences,
      alternatives:input.alternatives,uncertainties:input.uncertainties,contexts,dependencies,informationGaps:input.informationGaps}
    if(input.presentations?.length)packet.presentations=input.presentations.map((presentation:any)=>({
      language:presentation.language,sourceLanguage:presentation.sourceLanguage,
      items:presentation.items.map((item:any)=>{
        if(!input.items.some((candidate:any)=>candidate.key===item.itemKey))nativeUnavailable()
        return {itemId:identity("item:"+item.itemKey),title:item.title,text:item.text}
      })
    }))
    if(input.followup){
      const previous=exactJSON(await channel.send("reads",contextJSON({formatVersion:1,projectId:channel.projectID,
        operation:"read_request",subject:{id:input.followup.requestId}}),signal),nativeUnavailable)
      const record=previous.data?.record
      if(previous.kind!=="read"||record?.family!=="requests"||record.key!==input.followup.requestId||
        record.payload.projectId!==channel.projectID)nativeUnavailable()
      packet.followup={requestId:record.key,requestDigest:record.payload.requestDigest,packetDigest:record.payload.packetDigest,
        items:input.followup.items.map((change:any)=>{
          if(change.itemKey!==null&&!input.items.some((item:any)=>item.key===change.itemKey))nativeUnavailable()
          return {previousItemId:change.previousItemId,itemId:change.itemKey===null?null:identity("item:"+change.itemKey),
            disposition:change.disposition,explanation:change.explanation}
        })}
    }
    const requestId=identity("approval-request")
    const command=contextJSON({formatVersion:1,projectId:channel.projectID,requestKey:identity("review-command"),
      operation:"request_approval",payload:{requestId,packet}},2*1024*1024)
    return {content:await send(command),metadata:{writhelmRequestId:requestId}}
  }
}
