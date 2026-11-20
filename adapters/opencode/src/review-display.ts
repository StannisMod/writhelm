// SPDX-License-Identifier: AGPL-3.0-only
// Pure shared selection: safe in both the native server and renderer, no GUI/Node imports.
export const reviewLanguagePattern=/^[a-z]{2,3}(?:-[A-Z][a-z]{3})?(?:-[A-Z]{2}|-[0-9]{3})?$/
export const languageMatches=(requested:string,available:string)=>
  requested===available||available===requested.split("-")[0]
export class ReviewDisplayPending extends Error{
  constructor(){super("REVIEW_DISPLAY_PREPARATION_REQUIRED")}
}
export function selectReviewDisplay(packet:any,language:string,sourceLanguage?:string){
  if(!reviewLanguagePattern.test(language))throw new ReviewDisplayPending()
  const presentations=packet.presentations??[]
  const presentation=presentations.find((p:any)=>p.language===language)??
    presentations.find((p:any)=>p.language===language.split("-")[0])
  if(presentation){
    const ids=packet.items.map((i:any)=>i.id).sort()
    if(JSON.stringify(presentation.items.map((i:any)=>i.itemId).sort())!==JSON.stringify(ids)||
      new Set(ids).size!==ids.length)throw new ReviewDisplayPending()
    return {kind:"presentation" as const,presentation}
  }
  if(sourceLanguage&&reviewLanguagePattern.test(sourceLanguage)&&languageMatches(language,sourceLanguage))
    return {kind:"source" as const,sourceLanguage}
  throw new ReviewDisplayPending()
}
export type ReviewDisplayScope={storeId:string;projectId:string;sessionID:string;requestId:string;requestDigest:string;packetDigest:string}
export type SourceLanguageDeclaration={formatVersion:1;scope:ReviewDisplayScope;language:string}
/** IPC metadata is accepted only for the exact current view, never from model-authored packet text. */
export function displaySourceLanguage(raw:string,view:Omit<ReviewDisplayScope,"storeId">):string|undefined{
  if(!raw)return undefined
  let value:any
  try{value=JSON.parse(raw)}catch{throw new ReviewDisplayPending()}
  if(!value||Object.keys(value).sort().join(",")!=="formatVersion,language,scope"||
    value.formatVersion!==1||!reviewLanguagePattern.test(value.language)||
    !value.scope||Object.keys(value.scope).sort().join(",")!=="packetDigest,projectId,requestDigest,requestId,sessionID,storeId"||
    typeof value.scope.storeId!=="string"||!/^[a-f0-9-]{36}$/.test(value.scope.storeId)||
    Object.entries(view).some(([k,v])=>value.scope[k]!==v))throw new ReviewDisplayPending()
  return value.language
}
