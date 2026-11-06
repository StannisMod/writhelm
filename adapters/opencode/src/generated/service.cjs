// Generated from the pinned Writhelm schema by Ajv 8.17.1.
"use strict";
module.exports = validate20;
module.exports.default = validate20;
const schema31 = {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","$ref":"#/$defs/result","$defs":{"uuid":{"type":"string","pattern":"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},"digest":{"type":"string","pattern":"^[0-9a-f]{64}$"},"offset":{"type":"integer","minimum":0,"maximum":2147483647},"generation":{"type":"integer","minimum":0,"maximum":9223372036854776000},"text":{"type":"string","minLength":1},"texts":{"type":"array","items":{"$ref":"#/$defs/text"}},"clauseId":{"type":"string","minLength":1},"header":{"type":"object","additionalProperties":false,"required":["formatVersion","modelVersion"],"properties":{"formatVersion":{"const":1},"modelVersion":{"const":1}}},"documentKey":{"type":"object","additionalProperties":false,"required":["projectId","documentId"],"properties":{"projectId":{"$ref":"#/$defs/uuid"},"documentId":{"$ref":"#/$defs/uuid"}}},"revisionKey":{"type":"object","additionalProperties":false,"required":["document","revisionId"],"properties":{"document":{"$ref":"#/$defs/documentKey"},"revisionId":{"$ref":"#/$defs/uuid"}}},"subject":{"type":"object","additionalProperties":false,"required":["document","clauseId"],"properties":{"document":{"$ref":"#/$defs/documentKey"},"clauseId":{"oneOf":[{"$ref":"#/$defs/clauseId"},{"type":"null"}]}}},"clauseReference":{"type":"object","additionalProperties":false,"required":["kind","revision","revisionDigest","clauseId","contentDigest"],"properties":{"kind":{"const":"clause"},"revision":{"$ref":"#/$defs/revisionKey"},"revisionDigest":{"$ref":"#/$defs/digest"},"clauseId":{"$ref":"#/$defs/clauseId"},"contentDigest":{"$ref":"#/$defs/digest"}}},"contextReference":{"type":"object","additionalProperties":false,"required":["kind","revision","revisionDigest","contextDigest"],"properties":{"kind":{"const":"context"},"revision":{"$ref":"#/$defs/revisionKey"},"revisionDigest":{"$ref":"#/$defs/digest"},"contextDigest":{"$ref":"#/$defs/digest"}}},"reference":{"oneOf":[{"$ref":"#/$defs/clauseReference"},{"$ref":"#/$defs/contextReference"}]},"references":{"type":"array","uniqueItems":true,"items":{"$ref":"#/$defs/reference"}},"contexts":{"type":"array","uniqueItems":true,"items":{"$ref":"#/$defs/contextReference"}},"clauses":{"type":"array","uniqueItems":true,"items":{"$ref":"#/$defs/clauseReference"}},"range":{"type":"object","additionalProperties":false,"required":["start","endExclusive"],"properties":{"start":{"$ref":"#/$defs/offset"},"endExclusive":{"$ref":"#/$defs/offset"}}},"clauseSpan":{"type":"object","additionalProperties":false,"required":["id","span"],"properties":{"id":{"$ref":"#/$defs/clauseId"},"span":{"$ref":"#/$defs/range"}}},"structure":{"type":"object","additionalProperties":false,"required":["clauses","context"],"properties":{"clauses":{"type":"array","items":{"$ref":"#/$defs/clauseSpan"}},"context":{"type":"array","items":{"$ref":"#/$defs/range"}}}},"reviewItem":{"type":"object","additionalProperties":false,"required":["id","target","before"],"properties":{"id":{"$ref":"#/$defs/uuid"},"target":{"$ref":"#/$defs/reference"},"before":{"oneOf":[{"$ref":"#/$defs/reference"},{"type":"null"}]}}},"packet":{"type":"object","additionalProperties":false,"required":["items","question","scope","rationale","consequences","alternatives","uncertainties","contexts","dependencies","informationGaps"],"properties":{"items":{"type":"array","minItems":1,"items":{"$ref":"#/$defs/reviewItem"}},"question":{"$ref":"#/$defs/text"},"scope":{"$ref":"#/$defs/text"},"rationale":{"$ref":"#/$defs/text"},"consequences":{"allOf":[{"$ref":"#/$defs/texts"},{"minItems":1}]},"alternatives":{"allOf":[{"$ref":"#/$defs/texts"},{"minItems":1}]},"uncertainties":{"$ref":"#/$defs/texts"},"contexts":{"$ref":"#/$defs/contexts"},"dependencies":{"$ref":"#/$defs/references"},"informationGaps":{"$ref":"#/$defs/texts"}}},"comment":{"type":"object","additionalProperties":false,"required":["itemId","text"],"properties":{"itemId":{"$ref":"#/$defs/uuid"},"text":{"$ref":"#/$defs/text"}}},"answer":{"oneOf":[{"type":"object","additionalProperties":false,"required":["kind"],"properties":{"kind":{"enum":["approve","reject"]}}},{"type":"object","additionalProperties":false,"required":["kind","comments"],"properties":{"kind":{"const":"changes-required"},"comments":{"type":"array","minItems":1,"items":{"$ref":"#/$defs/comment"}}}}]},"guard":{"type":"object","additionalProperties":false,"required":["subject","generation"],"properties":{"subject":{"$ref":"#/$defs/subject"},"generation":{"$ref":"#/$defs/generation"}}},"link":{"type":"object","additionalProperties":false,"required":["source","target"],"properties":{"source":{"$ref":"#/$defs/reference"},"target":{"$ref":"#/$defs/reference"}}},"trace":{"type":"object","additionalProperties":false,"required":["origin","requestId","approvedBy","assessmentIds"],"properties":{"origin":{"$ref":"#/$defs/reference"},"requestId":{"$ref":"#/$defs/uuid"},"approvedBy":{"$ref":"#/$defs/uuid"},"assessmentIds":{"type":"array","items":{"$ref":"#/$defs/uuid"}}}},"provenanceEntry":{"type":"object","additionalProperties":false,"required":["reference","trace"],"properties":{"reference":{"$ref":"#/$defs/reference"},"trace":{"$ref":"#/$defs/trace"}}},"grantKey":{"type":"object","additionalProperties":false,"required":["principalId","projectId"],"properties":{"principalId":{"$ref":"#/$defs/uuid"},"projectId":{"$ref":"#/$defs/uuid"}}},"revisionPayload":{"type":"object","additionalProperties":false,"required":["bodyDigest","bodyBytes","structure","structureDigest","revisionDigest","contextDigest"],"properties":{"bodyDigest":{"$ref":"#/$defs/digest"},"bodyBytes":{"$ref":"#/$defs/offset"},"structure":{"$ref":"#/$defs/structure"},"structureDigest":{"$ref":"#/$defs/digest"},"revisionDigest":{"$ref":"#/$defs/digest"},"contextDigest":{"$ref":"#/$defs/digest"}}},"proposalPayload":{"type":"object","additionalProperties":false,"required":["principalId"],"properties":{"principalId":{"$ref":"#/$defs/uuid"}}},"grantPayload":{"type":"object","additionalProperties":false,"required":["capabilities"],"properties":{"capabilities":{"type":"array","uniqueItems":true,"items":{"enum":["READ","PROPOSE","PLAN","APPROVE","MANAGE"]}}}},"requestPayload":{"type":"object","additionalProperties":false,"required":["projectId","proposedBy","packet","generations","packetDigest","requestDigest"],"properties":{"projectId":{"$ref":"#/$defs/uuid"},"proposedBy":{"$ref":"#/$defs/uuid"},"packet":{"$ref":"#/$defs/packet"},"generations":{"type":"array","items":{"$ref":"#/$defs/guard"}},"packetDigest":{"$ref":"#/$defs/digest"},"requestDigest":{"$ref":"#/$defs/digest"}}},"resolutionPayload":{"type":"object","additionalProperties":false,"required":["actorId","answer","approved"],"properties":{"actorId":{"$ref":"#/$defs/uuid"},"answer":{"$ref":"#/$defs/answer"},"approved":{"$ref":"#/$defs/references"}}},"continuityPayload":{"type":"object","additionalProperties":false,"required":["source","assessmentId"],"properties":{"source":{"$ref":"#/$defs/reference"},"assessmentId":{"$ref":"#/$defs/uuid"}}},"assessmentPayload":{"type":"object","additionalProperties":false,"required":["projectId","actorId","references","rationale","links","blocked"],"properties":{"projectId":{"$ref":"#/$defs/uuid"},"actorId":{"$ref":"#/$defs/uuid"},"references":{"$ref":"#/$defs/references"},"rationale":{"$ref":"#/$defs/text"},"links":{"type":"array","items":{"$ref":"#/$defs/link"}},"blocked":{"$ref":"#/$defs/references"}}},"manifestPayload":{"type":"object","additionalProperties":false,"required":["consumerId","projectId","createdBy","clauses","contexts","dependencies","assessmentId","provenance"],"properties":{"consumerId":{"$ref":"#/$defs/uuid"},"projectId":{"$ref":"#/$defs/uuid"},"createdBy":{"$ref":"#/$defs/uuid"},"clauses":{"allOf":[{"$ref":"#/$defs/clauses"},{"minItems":1}]},"contexts":{"$ref":"#/$defs/contexts"},"dependencies":{"$ref":"#/$defs/references"},"assessmentId":{"$ref":"#/$defs/uuid"},"provenance":{"type":"array","items":{"$ref":"#/$defs/provenanceEntry"}}}},"headPayload":{"type":"object","additionalProperties":false,"required":["reference"],"properties":{"reference":{"$ref":"#/$defs/reference"}}},"generationPayload":{"type":"object","additionalProperties":false,"required":["generation"],"properties":{"generation":{"$ref":"#/$defs/generation"}}},"emptyPayload":{"type":"object","additionalProperties":false,"maxProperties":0},"record":{"type":"object","additionalProperties":false,"required":["formatVersion","family","key","payload"],"properties":{"formatVersion":{"const":1},"family":{"type":"string"},"key":{},"payload":{}},"oneOf":[{"properties":{"family":{"const":"revisions"},"key":{"$ref":"#/$defs/revisionKey"},"payload":{"$ref":"#/$defs/revisionPayload"}}},{"properties":{"family":{"const":"proposals"},"key":{"$ref":"#/$defs/revisionKey"},"payload":{"$ref":"#/$defs/proposalPayload"}}},{"properties":{"family":{"const":"grants"},"key":{"$ref":"#/$defs/grantKey"},"payload":{"$ref":"#/$defs/grantPayload"}}},{"properties":{"family":{"const":"requests"},"key":{"$ref":"#/$defs/uuid"},"payload":{"$ref":"#/$defs/requestPayload"}}},{"properties":{"family":{"const":"resolutions"},"key":{"$ref":"#/$defs/uuid"},"payload":{"$ref":"#/$defs/resolutionPayload"}}},{"properties":{"family":{"const":"approvals"},"key":{"$ref":"#/$defs/reference"},"payload":{"$ref":"#/$defs/trace"}}},{"properties":{"family":{"const":"continuity"},"key":{"$ref":"#/$defs/reference"},"payload":{"$ref":"#/$defs/continuityPayload"}}},{"properties":{"family":{"const":"assessments"},"key":{"$ref":"#/$defs/uuid"},"payload":{"$ref":"#/$defs/assessmentPayload"}}},{"properties":{"family":{"const":"manifests"},"key":{"$ref":"#/$defs/uuid"},"payload":{"$ref":"#/$defs/manifestPayload"}}},{"properties":{"family":{"const":"heads"},"key":{"$ref":"#/$defs/subject"},"payload":{"$ref":"#/$defs/headPayload"}}},{"properties":{"family":{"const":"generations"},"key":{"$ref":"#/$defs/subject"},"payload":{"$ref":"#/$defs/generationPayload"}}},{"properties":{"family":{"const":"blocked"},"key":{"$ref":"#/$defs/reference"},"payload":{"$ref":"#/$defs/emptyPayload"}}}]},"proposePayload":{"type":"object","additionalProperties":false,"required":["revisionKey","body","structure"],"properties":{"revisionKey":{"$ref":"#/$defs/revisionKey"},"body":{"type":"string"},"structure":{"$ref":"#/$defs/structure"}}},"newRequestPayload":{"type":"object","additionalProperties":false,"required":["requestId","packet"],"properties":{"requestId":{"$ref":"#/$defs/uuid"},"packet":{"$ref":"#/$defs/packet"}}},"resolvePayload":{"type":"object","additionalProperties":false,"required":["requestId","requestDigest","packetDigest","answer"],"properties":{"requestId":{"$ref":"#/$defs/uuid"},"requestDigest":{"$ref":"#/$defs/digest"},"packetDigest":{"$ref":"#/$defs/digest"},"answer":{"$ref":"#/$defs/answer"}}},"newAssessmentPayload":{"type":"object","additionalProperties":false,"required":["assessmentId","references","rationale","links","blocked"],"properties":{"assessmentId":{"$ref":"#/$defs/uuid"},"references":{"$ref":"#/$defs/references"},"rationale":{"$ref":"#/$defs/text"},"links":{"type":"array","items":{"$ref":"#/$defs/link"}},"blocked":{"$ref":"#/$defs/references"}}},"pinPayload":{"type":"object","additionalProperties":false,"required":["manifestId","consumerId","clauses","contexts","dependencies","assessmentId"],"properties":{"manifestId":{"$ref":"#/$defs/uuid"},"consumerId":{"$ref":"#/$defs/uuid"},"clauses":{"allOf":[{"$ref":"#/$defs/clauses"},{"minItems":1}]},"contexts":{"$ref":"#/$defs/contexts"},"dependencies":{"$ref":"#/$defs/references"},"assessmentId":{"$ref":"#/$defs/uuid"}}},"revokePayload":{"type":"object","additionalProperties":false,"required":["principalId"],"properties":{"principalId":{"$ref":"#/$defs/uuid"}}},"command":{"type":"object","additionalProperties":false,"required":["formatVersion","requestKey","projectId","operation","payload"],"properties":{"formatVersion":{"const":1},"requestKey":{"$ref":"#/$defs/uuid"},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"type":"string"},"payload":{}},"oneOf":[{"properties":{"operation":{"const":"propose_revision"},"payload":{"$ref":"#/$defs/proposePayload"}}},{"properties":{"operation":{"const":"request_approval"},"payload":{"$ref":"#/$defs/newRequestPayload"}}},{"properties":{"operation":{"const":"resolve_approval"},"payload":{"$ref":"#/$defs/resolvePayload"}}},{"properties":{"operation":{"const":"record_assessment"},"payload":{"$ref":"#/$defs/newAssessmentPayload"}}},{"properties":{"operation":{"const":"pin_consumer"},"payload":{"$ref":"#/$defs/pinPayload"}}},{"properties":{"operation":{"const":"revoke_access"},"payload":{"$ref":"#/$defs/revokePayload"}}}]},"capabilities":{"type":"array","uniqueItems":true,"items":{"enum":["READ","PROPOSE","PLAN","APPROVE","MANAGE"]}},"principal":{"type":"object","additionalProperties":false,"maxProperties":0},"project":{"type":"object","additionalProperties":false,"required":["ownerPrincipalId","profileVersion"],"properties":{"ownerPrincipalId":{"$ref":"#/$defs/uuid"},"profileVersion":{"const":1}}},"credential":{"type":"object","additionalProperties":false,"required":["storeId","projectId","principalId","verifierVersion","verifierDigest","status","generation","replacedBy"],"properties":{"storeId":{"$ref":"#/$defs/uuid"},"projectId":{"$ref":"#/$defs/uuid"},"principalId":{"$ref":"#/$defs/uuid"},"verifierVersion":{"const":1},"verifierDigest":{"$ref":"#/$defs/digest"},"status":{"enum":["active","revoked"]},"generation":{"$ref":"#/$defs/generation"},"replacedBy":{"oneOf":[{"type":"null"},{"$ref":"#/$defs/uuid"}]}},"if":{"properties":{"status":{"const":"active"}}},"then":{"properties":{"replacedBy":{"type":"null"}}}},"replaceGrants":{"type":"object","additionalProperties":false,"required":["principalId","expectedGeneration","expectedGrantDigest","capabilities"],"properties":{"principalId":{"$ref":"#/$defs/uuid"},"expectedGeneration":{"$ref":"#/$defs/generation"},"expectedGrantDigest":{"$ref":"#/$defs/digest"},"capabilities":{"$ref":"#/$defs/capabilities"}}},"revokeCredential":{"type":"object","additionalProperties":false,"required":["credentialId","expectedGeneration"],"properties":{"credentialId":{"$ref":"#/$defs/uuid"},"expectedGeneration":{"$ref":"#/$defs/generation"}}},"rotateCredential":{"type":"object","additionalProperties":false,"required":["credentialId","expectedGeneration","newCredentialId","verifierVersion","newVerifierDigest"],"properties":{"credentialId":{"$ref":"#/$defs/uuid"},"expectedGeneration":{"$ref":"#/$defs/generation"},"newCredentialId":{"$ref":"#/$defs/uuid"},"verifierVersion":{"const":1},"newVerifierDigest":{"$ref":"#/$defs/digest"}}},"coreOperation":{"enum":["propose_revision","request_approval","resolve_approval","record_assessment","pin_consumer","revoke_access"]},"authOperation":{"enum":["replace_grants","revoke_credential","rotate_credential"]},"operation":{"oneOf":[{"$ref":"#/$defs/coreOperation"},{"$ref":"#/$defs/authOperation"}]},"receiptKey":{"type":"object","additionalProperties":false,"required":["principalId","projectId","operation","requestKey"],"properties":{"principalId":{"$ref":"#/$defs/uuid"},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"$ref":"#/$defs/operation"},"requestKey":{"$ref":"#/$defs/uuid"}}},"gitOid":{"type":"object","additionalProperties":false,"required":["algorithm","hex"],"properties":{"algorithm":{"enum":["sha1","sha256"]},"hex":{"type":"string"}},"oneOf":[{"properties":{"algorithm":{"const":"sha1"},"hex":{"pattern":"^[0-9a-f]{40}$"}}},{"properties":{"algorithm":{"const":"sha256"},"hex":{"pattern":"^[0-9a-f]{64}$"}}}]},"nullableDigest":{"oneOf":[{"type":"null"},{"$ref":"#/$defs/digest"}]},"write":{"type":"object","additionalProperties":false,"required":["namespace","family","key","beforeDigest","afterDigest"],"properties":{"namespace":{"enum":["core","auth"]},"family":{"type":"string"},"key":{},"beforeDigest":{"$ref":"#/$defs/nullableDigest"},"afterDigest":{"$ref":"#/$defs/nullableDigest"}},"oneOf":[{"properties":{"namespace":{"const":"core"},"family":{"enum":["revisions","proposals"]},"key":{"$ref":"#/$defs/revisionKey"}}},{"properties":{"namespace":{"const":"core"},"family":{"const":"grants"},"key":{"$ref":"#/$defs/grantKey"}}},{"properties":{"namespace":{"const":"core"},"family":{"enum":["requests","resolutions","assessments","manifests"]},"key":{"$ref":"#/$defs/uuid"}}},{"properties":{"namespace":{"const":"core"},"family":{"enum":["approvals","continuity","blocked"]},"key":{"$ref":"#/$defs/reference"}}},{"properties":{"namespace":{"const":"core"},"family":{"enum":["heads","generations"]},"key":{"$ref":"#/$defs/subject"}}},{"properties":{"namespace":{"const":"core"},"family":{"const":"bodies"},"key":{"$ref":"#/$defs/digest"}}},{"properties":{"namespace":{"const":"auth"},"family":{"enum":["principals","projects","credentials"]},"key":{"$ref":"#/$defs/uuid"}}},{"properties":{"namespace":{"const":"auth"},"family":{"const":"grant-guards"},"key":{"$ref":"#/$defs/grantKey"}}}],"not":{"properties":{"beforeDigest":{"type":"null"},"afterDigest":{"type":"null"}}}},"success":{"type":"object","additionalProperties":false,"required":["kind","operation","result","domainReplayed"],"properties":{"kind":{"const":"success"},"operation":{"$ref":"#/$defs/operation"},"result":{},"domainReplayed":{"type":"boolean"}},"oneOf":[{"properties":{"operation":{"const":"propose_revision"},"result":{"$ref":"#/$defs/revisionKey"}}},{"properties":{"operation":{"enum":["request_approval","resolve_approval","record_assessment","pin_consumer","revoke_access","replace_grants","revoke_credential"]},"result":{"$ref":"#/$defs/uuid"}}},{"properties":{"operation":{"const":"rotate_credential"},"result":{"type":"object","additionalProperties":false,"required":["credentialId","replacedCredentialId"],"properties":{"credentialId":{"$ref":"#/$defs/uuid"},"replacedCredentialId":{"$ref":"#/$defs/uuid"}}}}}]},"rejection":{"type":"object","additionalProperties":false,"required":["kind","domain","code"],"properties":{"kind":{"const":"rejected"},"domain":{"enum":["core","auth"]},"code":{"type":"string"}},"oneOf":[{"properties":{"domain":{"const":"core"},"code":{"enum":["NOT_FOUND","DUPLICATE_ID","INVALID_REFERENCE","FOREIGN_REFERENCE","MISSING_CONTEXT","STALE_SUBJECT","DIGEST_MISMATCH","ALREADY_RESOLVED","UNKNOWN_COMMENT_ITEM","INCOMPLETE_PACKET","UNAPPROVED_INPUT","BLOCKED_INPUT","INVALID_CONTINUITY","CONTEXT_CHANGED","INCOMPLETE_ASSESSMENT","CONFLICTING_SELECTION","UNSUPPORTED_CONTEXT_MIX","LAST_AUTHORITY","GENERATION_EXHAUSTED"]}}},{"properties":{"domain":{"const":"auth"},"code":{"enum":["AUTH_TARGET_NOT_FOUND","AUTH_STALE_CREDENTIAL","AUTH_STALE_GRANT","AUTH_ALREADY_REVOKED","AUTH_ALREADY_REPLACED","AUTH_DUPLICATE_CREDENTIAL","AUTH_OWNER_LOCKOUT","AUTH_FIXED_OWNER_POLICY","AUTH_GENERATION_EXHAUSTED"]}}}]},"receipt":{"type":"object","additionalProperties":false,"required":["formatVersion","family","key","payload"],"properties":{"formatVersion":{"const":2},"family":{"const":"command-receipt"},"key":{"$ref":"#/$defs/receiptKey"},"payload":{"type":"object","additionalProperties":false,"required":["storeId","fingerprintVersion","fingerprint","validatedAt","authenticatedByCredentialId","outcome","writes"],"properties":{"storeId":{"$ref":"#/$defs/uuid"},"fingerprintVersion":{"enum":[1,2]},"fingerprint":{"$ref":"#/$defs/digest"},"validatedAt":{"$ref":"#/$defs/gitOid"},"authenticatedByCredentialId":{"$ref":"#/$defs/uuid"},"outcome":{"oneOf":[{"$ref":"#/$defs/success"},{"$ref":"#/$defs/rejection"}]},"writes":{"type":"array","items":{"$ref":"#/$defs/write"}}}}},"allOf":[{"if":{"properties":{"key":{"properties":{"operation":{"$ref":"#/$defs/coreOperation"}}}}},"then":{"properties":{"payload":{"properties":{"fingerprintVersion":{"const":1}}}}},"else":{"properties":{"payload":{"properties":{"fingerprintVersion":{"const":2}}}}}},{"if":{"properties":{"payload":{"properties":{"outcome":{"properties":{"kind":{"const":"rejected"}}}}}}},"then":{"properties":{"payload":{"properties":{"writes":{"maxItems":0}}}}}}]},"httpCommand":{"oneOf":[{"$ref":"#/$defs/command"},{"type":"object","additionalProperties":false,"required":["formatVersion","requestKey","projectId","operation","payload"],"properties":{"formatVersion":{"const":2},"requestKey":{"$ref":"#/$defs/uuid"},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"type":"string"},"payload":{}},"oneOf":[{"properties":{"operation":{"const":"replace_grants"},"payload":{"$ref":"#/$defs/replaceGrants"}}},{"properties":{"operation":{"const":"revoke_credential"},"payload":{"$ref":"#/$defs/revokeCredential"}}},{"properties":{"operation":{"const":"rotate_credential"},"payload":{"$ref":"#/$defs/rotateCredential"}}}]}]},"agentCommand":{"type":"object","additionalProperties":false,"required":["formatVersion","requestKey","projectId","operation","payload"],"properties":{"formatVersion":{"const":1},"requestKey":{"$ref":"#/$defs/uuid"},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"type":"string"},"payload":{}},"oneOf":[{"properties":{"operation":{"const":"propose_revision"},"payload":{"$ref":"#/$defs/proposePayload"}}},{"properties":{"operation":{"const":"request_approval"},"payload":{"$ref":"#/$defs/newRequestPayload"}}},{"properties":{"operation":{"const":"record_assessment"},"payload":{"$ref":"#/$defs/newAssessmentPayload"}}},{"properties":{"operation":{"const":"pin_consumer"},"payload":{"$ref":"#/$defs/pinPayload"}}}]},"cursor":{"oneOf":[{"type":"string","pattern":"^[0-9a-f-]{36}$"},{"type":"null"}]},"pageSize":{"type":"integer","minimum":1,"maximum":32},"read":{"oneOf":[{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"type":"null"},"operation":{"const":"list_projects"},"subject":{"type":"object","additionalProperties":false,"properties":{"cursor":{"$ref":"#/$defs/cursor"},"pageSize":{"$ref":"#/$defs/pageSize"}},"required":["cursor","pageSize"]}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"list_documents"},"subject":{"type":"object","additionalProperties":false,"properties":{"cursor":{"$ref":"#/$defs/cursor"},"pageSize":{"$ref":"#/$defs/pageSize"}},"required":["cursor","pageSize"]}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"list_revisions"},"subject":{"type":"object","additionalProperties":false,"properties":{"documentId":{"$ref":"#/$defs/uuid"},"cursor":{"$ref":"#/$defs/cursor"},"pageSize":{"$ref":"#/$defs/pageSize"}},"required":["documentId","cursor","pageSize"]}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"list_requests"},"subject":{"type":"object","additionalProperties":false,"properties":{"pendingOnly":{"type":"boolean"},"cursor":{"$ref":"#/$defs/cursor"},"pageSize":{"$ref":"#/$defs/pageSize"}},"required":["pendingOnly","cursor","pageSize"]}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"list_manifests"},"subject":{"type":"object","additionalProperties":false,"properties":{"cursor":{"$ref":"#/$defs/cursor"},"pageSize":{"$ref":"#/$defs/pageSize"}},"required":["cursor","pageSize"]}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"read_revision"},"subject":{"type":"object","additionalProperties":false,"properties":{"revisionKey":{"$ref":"#/$defs/revisionKey"},"revisionDigest":{"$ref":"#/$defs/digest"}},"required":["revisionKey","revisionDigest"]}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"read_clause"},"subject":{"$ref":"#/$defs/clauseReference"}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"read_context"},"subject":{"$ref":"#/$defs/contextReference"}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"read_request"},"subject":{"type":"object","additionalProperties":false,"properties":{"id":{"$ref":"#/$defs/uuid"}},"required":["id"]}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"read_resolution"},"subject":{"type":"object","additionalProperties":false,"properties":{"id":{"$ref":"#/$defs/uuid"}},"required":["id"]}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"read_manifest"},"subject":{"type":"object","additionalProperties":false,"properties":{"id":{"$ref":"#/$defs/uuid"}},"required":["id"]}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"read_approval_trace"},"subject":{"$ref":"#/$defs/reference"}},"required":["formatVersion","projectId","operation","subject"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"const":"read_review_bundle"},"subject":{"type":"object","additionalProperties":false,"properties":{"id":{"$ref":"#/$defs/uuid"},"requestDigest":{"$ref":"#/$defs/digest"},"packetDigest":{"$ref":"#/$defs/digest"}},"required":["id","requestDigest","packetDigest"]}},"required":["formatVersion","projectId","operation","subject"]}]},"oid":{"type":"object","additionalProperties":false,"required":["algorithm","hex"],"properties":{"algorithm":{"enum":["sha1","sha256"]},"hex":{"type":"string"}},"oneOf":[{"properties":{"algorithm":{"const":"sha1"},"hex":{"pattern":"^[0-9a-f]{40}$"}}},{"properties":{"algorithm":{"const":"sha256"},"hex":{"pattern":"^[0-9a-f]{64}$"}}}]},"material":{"type":"object","additionalProperties":false,"properties":{"reference":{"$ref":"#/$defs/reference"},"fragments":{"type":"array","items":{"type":"object","additionalProperties":false,"properties":{"span":{"$ref":"#/$defs/range"},"text":{"type":"string"}},"required":["span","text"]}},"observation":{"type":"string"},"trace":{"oneOf":[{"$ref":"#/$defs/trace"},{"type":"null"}]}},"required":["reference","fragments","observation","trace"]},"revisionRead":{"type":"object","additionalProperties":false,"properties":{"revisionKey":{"$ref":"#/$defs/revisionKey"},"revisionDigest":{"$ref":"#/$defs/digest"},"bodyDigest":{"$ref":"#/$defs/digest"},"structure":{"$ref":"#/$defs/structure"},"body":{"type":"string"},"proposedBy":{"$ref":"#/$defs/uuid"}},"required":["revisionKey","revisionDigest","bodyDigest","structure","body","proposedBy"]},"bundle":{"type":"object","additionalProperties":false,"properties":{"request":{"$ref":"#/$defs/record"},"resolution":{"oneOf":[{"$ref":"#/$defs/record"},{"type":"null"}]},"materials":{"type":"array","items":{"$ref":"#/$defs/material"}},"actionable":{"type":"boolean"},"blockers":{"type":"array","items":{"type":"string"}},"complete":{"const":true}},"required":["request","resolution","materials","actionable","blockers","complete"]},"listItem":{"oneOf":[{"type":"object","additionalProperties":false,"properties":{"projectId":{"$ref":"#/$defs/uuid"}},"required":["projectId"]},{"type":"object","additionalProperties":false,"properties":{"document":{"$ref":"#/$defs/documentKey"}},"required":["document"]},{"type":"object","additionalProperties":false,"properties":{"revisionKey":{"$ref":"#/$defs/revisionKey"},"revisionDigest":{"$ref":"#/$defs/digest"}},"required":["revisionKey","revisionDigest"]},{"type":"object","additionalProperties":false,"properties":{"id":{"$ref":"#/$defs/uuid"},"requestDigest":{"$ref":"#/$defs/digest"},"packetDigest":{"$ref":"#/$defs/digest"},"pending":{"type":"boolean"}},"required":["id","requestDigest","packetDigest","pending"]},{"type":"object","additionalProperties":false,"properties":{"id":{"$ref":"#/$defs/uuid"},"consumerId":{"$ref":"#/$defs/uuid"}},"required":["id","consumerId"]}]},"page":{"type":"object","additionalProperties":false,"properties":{"snapshotId":{"$ref":"#/$defs/uuid"},"nextCursor":{"$ref":"#/$defs/cursor"},"complete":{"type":"boolean"},"items":{"type":"array","items":{"$ref":"#/$defs/listItem"}}},"required":["snapshotId","nextCursor","complete","items"]},"errorCode":{"enum":["UNAUTHENTICATED","FORBIDDEN","INVALID_INPUT","UNSUPPORTED","INGRESS_TOO_LARGE","RATE_LIMIT","UNAVAILABLE","SNAPSHOT_EXPIRED","NOT_FOUND","IDENTITY_CONFLICT","INTEGRITY","PUBLICATION_UNKNOWN","INCOMPLETE_PACKET"]},"result":{"oneOf":[{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"list_projects"},"data":{"$ref":"#/$defs/page"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"list_documents"},"data":{"$ref":"#/$defs/page"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"list_revisions"},"data":{"$ref":"#/$defs/page"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"list_requests"},"data":{"$ref":"#/$defs/page"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"list_manifests"},"data":{"$ref":"#/$defs/page"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_revision"},"data":{"$ref":"#/$defs/revisionRead"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_clause"},"data":{"$ref":"#/$defs/material"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_context"},"data":{"$ref":"#/$defs/material"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_request"},"data":{"type":"object","additionalProperties":false,"properties":{"record":{"$ref":"#/$defs/record"}},"required":["record"]}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_resolution"},"data":{"type":"object","additionalProperties":false,"properties":{"record":{"$ref":"#/$defs/record"}},"required":["record"]}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_manifest"},"data":{"type":"object","additionalProperties":false,"properties":{"record":{"$ref":"#/$defs/record"}},"required":["record"]}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_approval_trace"},"data":{"$ref":"#/$defs/material"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_review_bundle"},"data":{"$ref":"#/$defs/bundle"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"settled"},"identity":{"$ref":"#/$defs/receiptKey"},"fingerprint":{"$ref":"#/$defs/digest"},"validatedAt":{"$ref":"#/$defs/oid"},"authenticatedByCredentialId":{"$ref":"#/$defs/uuid"},"outcome":{"oneOf":[{"$ref":"#/$defs/success"},{"$ref":"#/$defs/rejection"}]},"commandReplayed":{"type":"boolean"},"publication":{"oneOf":[{"$ref":"#/$defs/oid"},{"type":"null"}]}},"required":["formatVersion","kind","identity","fingerprint","validatedAt","authenticatedByCredentialId","outcome","commandReplayed","publication"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"error"},"code":{"$ref":"#/$defs/errorCode"}},"required":["formatVersion","kind","code"]}]}}};
const schema32 = {"oneOf":[{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"list_projects"},"data":{"$ref":"#/$defs/page"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"list_documents"},"data":{"$ref":"#/$defs/page"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"list_revisions"},"data":{"$ref":"#/$defs/page"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"list_requests"},"data":{"$ref":"#/$defs/page"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"list_manifests"},"data":{"$ref":"#/$defs/page"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_revision"},"data":{"$ref":"#/$defs/revisionRead"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_clause"},"data":{"$ref":"#/$defs/material"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_context"},"data":{"$ref":"#/$defs/material"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_request"},"data":{"type":"object","additionalProperties":false,"properties":{"record":{"$ref":"#/$defs/record"}},"required":["record"]}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_resolution"},"data":{"type":"object","additionalProperties":false,"properties":{"record":{"$ref":"#/$defs/record"}},"required":["record"]}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_manifest"},"data":{"type":"object","additionalProperties":false,"properties":{"record":{"$ref":"#/$defs/record"}},"required":["record"]}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_approval_trace"},"data":{"$ref":"#/$defs/material"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"read"},"basis":{"$ref":"#/$defs/oid"},"operation":{"const":"read_review_bundle"},"data":{"$ref":"#/$defs/bundle"}},"required":["formatVersion","kind","basis","operation","data"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"settled"},"identity":{"$ref":"#/$defs/receiptKey"},"fingerprint":{"$ref":"#/$defs/digest"},"validatedAt":{"$ref":"#/$defs/oid"},"authenticatedByCredentialId":{"$ref":"#/$defs/uuid"},"outcome":{"oneOf":[{"$ref":"#/$defs/success"},{"$ref":"#/$defs/rejection"}]},"commandReplayed":{"type":"boolean"},"publication":{"oneOf":[{"$ref":"#/$defs/oid"},{"type":"null"}]}},"required":["formatVersion","kind","identity","fingerprint","validatedAt","authenticatedByCredentialId","outcome","commandReplayed","publication"]},{"type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"kind":{"const":"error"},"code":{"$ref":"#/$defs/errorCode"}},"required":["formatVersion","kind","code"]}]};
const schema33 = {"type":"object","additionalProperties":false,"required":["algorithm","hex"],"properties":{"algorithm":{"enum":["sha1","sha256"]},"hex":{"type":"string"}},"oneOf":[{"properties":{"algorithm":{"const":"sha1"},"hex":{"pattern":"^[0-9a-f]{40}$"}}},{"properties":{"algorithm":{"const":"sha256"},"hex":{"pattern":"^[0-9a-f]{64}$"}}}]};
const schema44 = {"type":"string","pattern":"^[0-9a-f]{64}$"};
const schema35 = {"type":"string","pattern":"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"};
const schema160 = {"type":"object","additionalProperties":false,"required":["kind","domain","code"],"properties":{"kind":{"const":"rejected"},"domain":{"enum":["core","auth"]},"code":{"type":"string"}},"oneOf":[{"properties":{"domain":{"const":"core"},"code":{"enum":["NOT_FOUND","DUPLICATE_ID","INVALID_REFERENCE","FOREIGN_REFERENCE","MISSING_CONTEXT","STALE_SUBJECT","DIGEST_MISMATCH","ALREADY_RESOLVED","UNKNOWN_COMMENT_ITEM","INCOMPLETE_PACKET","UNAPPROVED_INPUT","BLOCKED_INPUT","INVALID_CONTINUITY","CONTEXT_CHANGED","INCOMPLETE_ASSESSMENT","CONFLICTING_SELECTION","UNSUPPORTED_CONTEXT_MIX","LAST_AUTHORITY","GENERATION_EXHAUSTED"]}}},{"properties":{"domain":{"const":"auth"},"code":{"enum":["AUTH_TARGET_NOT_FOUND","AUTH_STALE_CREDENTIAL","AUTH_STALE_GRANT","AUTH_ALREADY_REVOKED","AUTH_ALREADY_REPLACED","AUTH_DUPLICATE_CREDENTIAL","AUTH_OWNER_LOCKOUT","AUTH_FIXED_OWNER_POLICY","AUTH_GENERATION_EXHAUSTED"]}}}]};
const schema162 = {"enum":["UNAUTHENTICATED","FORBIDDEN","INVALID_INPUT","UNSUPPORTED","INGRESS_TOO_LARGE","RATE_LIMIT","UNAVAILABLE","SNAPSHOT_EXPIRED","NOT_FOUND","IDENTITY_CONFLICT","INTEGRITY","PUBLICATION_UNKNOWN","INCOMPLETE_PACKET"]};
const pattern4 = new RegExp("^[0-9a-f]{40}$", "u");
const pattern5 = new RegExp("^[0-9a-f]{64}$", "u");
const pattern6 = new RegExp("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", "u");
const schema34 = {"type":"object","additionalProperties":false,"properties":{"snapshotId":{"$ref":"#/$defs/uuid"},"nextCursor":{"$ref":"#/$defs/cursor"},"complete":{"type":"boolean"},"items":{"type":"array","items":{"$ref":"#/$defs/listItem"}}},"required":["snapshotId","nextCursor","complete","items"]};
const schema36 = {"oneOf":[{"type":"string","pattern":"^[0-9a-f-]{36}$"},{"type":"null"}]};
const pattern7 = new RegExp("^[0-9a-f-]{36}$", "u");
const schema37 = {"oneOf":[{"type":"object","additionalProperties":false,"properties":{"projectId":{"$ref":"#/$defs/uuid"}},"required":["projectId"]},{"type":"object","additionalProperties":false,"properties":{"document":{"$ref":"#/$defs/documentKey"}},"required":["document"]},{"type":"object","additionalProperties":false,"properties":{"revisionKey":{"$ref":"#/$defs/revisionKey"},"revisionDigest":{"$ref":"#/$defs/digest"}},"required":["revisionKey","revisionDigest"]},{"type":"object","additionalProperties":false,"properties":{"id":{"$ref":"#/$defs/uuid"},"requestDigest":{"$ref":"#/$defs/digest"},"packetDigest":{"$ref":"#/$defs/digest"},"pending":{"type":"boolean"}},"required":["id","requestDigest","packetDigest","pending"]},{"type":"object","additionalProperties":false,"properties":{"id":{"$ref":"#/$defs/uuid"},"consumerId":{"$ref":"#/$defs/uuid"}},"required":["id","consumerId"]}]};
const schema39 = {"type":"object","additionalProperties":false,"required":["projectId","documentId"],"properties":{"projectId":{"$ref":"#/$defs/uuid"},"documentId":{"$ref":"#/$defs/uuid"}}};

function validate24(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate24.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.projectId === undefined) && (missing0 = "projectId")) || ((data.documentId === undefined) && (missing0 = "documentId"))){
validate24.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "projectId") || (key0 === "documentId"))){
validate24.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.projectId !== undefined){
let data0 = data.projectId;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate24.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate24.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.documentId !== undefined){
let data1 = data.documentId;
const _errs5 = errors;
const _errs6 = errors;
if(errors === _errs6){
if(typeof data1 === "string"){
if(!pattern6.test(data1)){
validate24.errors = [{instancePath:instancePath+"/documentId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate24.errors = [{instancePath:instancePath+"/documentId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate24.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate24.errors = vErrors;
return errors === 0;
}
validate24.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema42 = {"type":"object","additionalProperties":false,"required":["document","revisionId"],"properties":{"document":{"$ref":"#/$defs/documentKey"},"revisionId":{"$ref":"#/$defs/uuid"}}};

function validate26(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate26.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.document === undefined) && (missing0 = "document")) || ((data.revisionId === undefined) && (missing0 = "revisionId"))){
validate26.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "document") || (key0 === "revisionId"))){
validate26.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.document !== undefined){
const _errs2 = errors;
if(!(validate24(data.document, {instancePath:instancePath+"/document",parentData:data,parentDataProperty:"document",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate24.errors : vErrors.concat(validate24.errors);
errors = vErrors.length;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.revisionId !== undefined){
let data1 = data.revisionId;
const _errs3 = errors;
const _errs4 = errors;
if(errors === _errs4){
if(typeof data1 === "string"){
if(!pattern6.test(data1)){
validate26.errors = [{instancePath:instancePath+"/revisionId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate26.errors = [{instancePath:instancePath+"/revisionId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate26.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate26.errors = vErrors;
return errors === 0;
}
validate26.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate23(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate23.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
const _errs0 = errors;
let valid0 = false;
let passing0 = null;
const _errs1 = errors;
if(errors === _errs1){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if((data.projectId === undefined) && (missing0 = "projectId")){
const err0 = {instancePath,schemaPath:"#/oneOf/0/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
else {
const _errs3 = errors;
for(const key0 in data){
if(!(key0 === "projectId")){
const err1 = {instancePath,schemaPath:"#/oneOf/0/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
break;
}
}
if(_errs3 === errors){
if(data.projectId !== undefined){
let data0 = data.projectId;
const _errs5 = errors;
if(errors === _errs5){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
const err2 = {instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err2];
}
else {
vErrors.push(err2);
}
errors++;
}
}
else {
const err3 = {instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err3];
}
else {
vErrors.push(err3);
}
errors++;
}
}
}
}
}
}
else {
const err4 = {instancePath,schemaPath:"#/oneOf/0/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err4];
}
else {
vErrors.push(err4);
}
errors++;
}
}
var _valid0 = _errs1 === errors;
if(_valid0){
valid0 = true;
passing0 = 0;
var props0 = true;
}
const _errs7 = errors;
if(errors === _errs7){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing1;
if((data.document === undefined) && (missing1 = "document")){
const err5 = {instancePath,schemaPath:"#/oneOf/1/required",keyword:"required",params:{missingProperty: missing1},message:"must have required property '"+missing1+"'"};
if(vErrors === null){
vErrors = [err5];
}
else {
vErrors.push(err5);
}
errors++;
}
else {
const _errs9 = errors;
for(const key1 in data){
if(!(key1 === "document")){
const err6 = {instancePath,schemaPath:"#/oneOf/1/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key1},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err6];
}
else {
vErrors.push(err6);
}
errors++;
break;
}
}
if(_errs9 === errors){
if(data.document !== undefined){
if(!(validate24(data.document, {instancePath:instancePath+"/document",parentData:data,parentDataProperty:"document",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate24.errors : vErrors.concat(validate24.errors);
errors = vErrors.length;
}
}
}
}
}
else {
const err7 = {instancePath,schemaPath:"#/oneOf/1/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err7];
}
else {
vErrors.push(err7);
}
errors++;
}
}
var _valid0 = _errs7 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid0 = true;
passing0 = 1;
if(props0 !== true){
props0 = true;
}
}
const _errs11 = errors;
if(errors === _errs11){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing2;
if(((data.revisionKey === undefined) && (missing2 = "revisionKey")) || ((data.revisionDigest === undefined) && (missing2 = "revisionDigest"))){
const err8 = {instancePath,schemaPath:"#/oneOf/2/required",keyword:"required",params:{missingProperty: missing2},message:"must have required property '"+missing2+"'"};
if(vErrors === null){
vErrors = [err8];
}
else {
vErrors.push(err8);
}
errors++;
}
else {
const _errs13 = errors;
for(const key2 in data){
if(!((key2 === "revisionKey") || (key2 === "revisionDigest"))){
const err9 = {instancePath,schemaPath:"#/oneOf/2/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key2},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err9];
}
else {
vErrors.push(err9);
}
errors++;
break;
}
}
if(_errs13 === errors){
if(data.revisionKey !== undefined){
const _errs14 = errors;
if(!(validate26(data.revisionKey, {instancePath:instancePath+"/revisionKey",parentData:data,parentDataProperty:"revisionKey",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate26.errors : vErrors.concat(validate26.errors);
errors = vErrors.length;
}
var valid4 = _errs14 === errors;
}
else {
var valid4 = true;
}
if(valid4){
if(data.revisionDigest !== undefined){
let data3 = data.revisionDigest;
const _errs15 = errors;
const _errs16 = errors;
if(errors === _errs16){
if(typeof data3 === "string"){
if(!pattern5.test(data3)){
const err10 = {instancePath:instancePath+"/revisionDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err10];
}
else {
vErrors.push(err10);
}
errors++;
}
}
else {
const err11 = {instancePath:instancePath+"/revisionDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err11];
}
else {
vErrors.push(err11);
}
errors++;
}
}
var valid4 = _errs15 === errors;
}
else {
var valid4 = true;
}
}
}
}
}
else {
const err12 = {instancePath,schemaPath:"#/oneOf/2/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err12];
}
else {
vErrors.push(err12);
}
errors++;
}
}
var _valid0 = _errs11 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 2];
}
else {
if(_valid0){
valid0 = true;
passing0 = 2;
if(props0 !== true){
props0 = true;
}
}
const _errs18 = errors;
if(errors === _errs18){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing3;
if(((((data.id === undefined) && (missing3 = "id")) || ((data.requestDigest === undefined) && (missing3 = "requestDigest"))) || ((data.packetDigest === undefined) && (missing3 = "packetDigest"))) || ((data.pending === undefined) && (missing3 = "pending"))){
const err13 = {instancePath,schemaPath:"#/oneOf/3/required",keyword:"required",params:{missingProperty: missing3},message:"must have required property '"+missing3+"'"};
if(vErrors === null){
vErrors = [err13];
}
else {
vErrors.push(err13);
}
errors++;
}
else {
const _errs20 = errors;
for(const key3 in data){
if(!((((key3 === "id") || (key3 === "requestDigest")) || (key3 === "packetDigest")) || (key3 === "pending"))){
const err14 = {instancePath,schemaPath:"#/oneOf/3/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key3},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err14];
}
else {
vErrors.push(err14);
}
errors++;
break;
}
}
if(_errs20 === errors){
if(data.id !== undefined){
let data4 = data.id;
const _errs21 = errors;
const _errs22 = errors;
if(errors === _errs22){
if(typeof data4 === "string"){
if(!pattern6.test(data4)){
const err15 = {instancePath:instancePath+"/id",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err15];
}
else {
vErrors.push(err15);
}
errors++;
}
}
else {
const err16 = {instancePath:instancePath+"/id",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err16];
}
else {
vErrors.push(err16);
}
errors++;
}
}
var valid6 = _errs21 === errors;
}
else {
var valid6 = true;
}
if(valid6){
if(data.requestDigest !== undefined){
let data5 = data.requestDigest;
const _errs24 = errors;
const _errs25 = errors;
if(errors === _errs25){
if(typeof data5 === "string"){
if(!pattern5.test(data5)){
const err17 = {instancePath:instancePath+"/requestDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err17];
}
else {
vErrors.push(err17);
}
errors++;
}
}
else {
const err18 = {instancePath:instancePath+"/requestDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err18];
}
else {
vErrors.push(err18);
}
errors++;
}
}
var valid6 = _errs24 === errors;
}
else {
var valid6 = true;
}
if(valid6){
if(data.packetDigest !== undefined){
let data6 = data.packetDigest;
const _errs27 = errors;
const _errs28 = errors;
if(errors === _errs28){
if(typeof data6 === "string"){
if(!pattern5.test(data6)){
const err19 = {instancePath:instancePath+"/packetDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err19];
}
else {
vErrors.push(err19);
}
errors++;
}
}
else {
const err20 = {instancePath:instancePath+"/packetDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err20];
}
else {
vErrors.push(err20);
}
errors++;
}
}
var valid6 = _errs27 === errors;
}
else {
var valid6 = true;
}
if(valid6){
if(data.pending !== undefined){
const _errs30 = errors;
if(typeof data.pending !== "boolean"){
const err21 = {instancePath:instancePath+"/pending",schemaPath:"#/oneOf/3/properties/pending/type",keyword:"type",params:{type: "boolean"},message:"must be boolean"};
if(vErrors === null){
vErrors = [err21];
}
else {
vErrors.push(err21);
}
errors++;
}
var valid6 = _errs30 === errors;
}
else {
var valid6 = true;
}
}
}
}
}
}
}
else {
const err22 = {instancePath,schemaPath:"#/oneOf/3/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err22];
}
else {
vErrors.push(err22);
}
errors++;
}
}
var _valid0 = _errs18 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 3];
}
else {
if(_valid0){
valid0 = true;
passing0 = 3;
if(props0 !== true){
props0 = true;
}
}
const _errs32 = errors;
if(errors === _errs32){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing4;
if(((data.id === undefined) && (missing4 = "id")) || ((data.consumerId === undefined) && (missing4 = "consumerId"))){
const err23 = {instancePath,schemaPath:"#/oneOf/4/required",keyword:"required",params:{missingProperty: missing4},message:"must have required property '"+missing4+"'"};
if(vErrors === null){
vErrors = [err23];
}
else {
vErrors.push(err23);
}
errors++;
}
else {
const _errs34 = errors;
for(const key4 in data){
if(!((key4 === "id") || (key4 === "consumerId"))){
const err24 = {instancePath,schemaPath:"#/oneOf/4/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key4},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err24];
}
else {
vErrors.push(err24);
}
errors++;
break;
}
}
if(_errs34 === errors){
if(data.id !== undefined){
let data8 = data.id;
const _errs35 = errors;
const _errs36 = errors;
if(errors === _errs36){
if(typeof data8 === "string"){
if(!pattern6.test(data8)){
const err25 = {instancePath:instancePath+"/id",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err25];
}
else {
vErrors.push(err25);
}
errors++;
}
}
else {
const err26 = {instancePath:instancePath+"/id",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err26];
}
else {
vErrors.push(err26);
}
errors++;
}
}
var valid10 = _errs35 === errors;
}
else {
var valid10 = true;
}
if(valid10){
if(data.consumerId !== undefined){
let data9 = data.consumerId;
const _errs38 = errors;
const _errs39 = errors;
if(errors === _errs39){
if(typeof data9 === "string"){
if(!pattern6.test(data9)){
const err27 = {instancePath:instancePath+"/consumerId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err27];
}
else {
vErrors.push(err27);
}
errors++;
}
}
else {
const err28 = {instancePath:instancePath+"/consumerId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err28];
}
else {
vErrors.push(err28);
}
errors++;
}
}
var valid10 = _errs38 === errors;
}
else {
var valid10 = true;
}
}
}
}
}
else {
const err29 = {instancePath,schemaPath:"#/oneOf/4/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err29];
}
else {
vErrors.push(err29);
}
errors++;
}
}
var _valid0 = _errs32 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 4];
}
else {
if(_valid0){
valid0 = true;
passing0 = 4;
if(props0 !== true){
props0 = true;
}
}
}
}
}
}
if(!valid0){
const err30 = {instancePath,schemaPath:"#/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err30];
}
else {
vErrors.push(err30);
}
errors++;
validate23.errors = vErrors;
return false;
}
else {
errors = _errs0;
if(vErrors !== null){
if(_errs0){
vErrors.length = _errs0;
}
else {
vErrors = null;
}
}
}
validate23.errors = vErrors;
evaluated0.props = props0;
return errors === 0;
}
validate23.evaluated = {"dynamicProps":true,"dynamicItems":false};


function validate22(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate22.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((data.snapshotId === undefined) && (missing0 = "snapshotId")) || ((data.nextCursor === undefined) && (missing0 = "nextCursor"))) || ((data.complete === undefined) && (missing0 = "complete"))) || ((data.items === undefined) && (missing0 = "items"))){
validate22.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((key0 === "snapshotId") || (key0 === "nextCursor")) || (key0 === "complete")) || (key0 === "items"))){
validate22.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.snapshotId !== undefined){
let data0 = data.snapshotId;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate22.errors = [{instancePath:instancePath+"/snapshotId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate22.errors = [{instancePath:instancePath+"/snapshotId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.nextCursor !== undefined){
let data1 = data.nextCursor;
const _errs5 = errors;
const _errs7 = errors;
let valid3 = false;
let passing0 = null;
const _errs8 = errors;
if(errors === _errs8){
if(typeof data1 === "string"){
if(!pattern7.test(data1)){
const err0 = {instancePath:instancePath+"/nextCursor",schemaPath:"#/$defs/cursor/oneOf/0/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f-]{36}$"},message:"must match pattern \""+"^[0-9a-f-]{36}$"+"\""};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
}
else {
const err1 = {instancePath:instancePath+"/nextCursor",schemaPath:"#/$defs/cursor/oneOf/0/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
}
}
var _valid0 = _errs8 === errors;
if(_valid0){
valid3 = true;
passing0 = 0;
}
const _errs10 = errors;
if(data1 !== null){
const err2 = {instancePath:instancePath+"/nextCursor",schemaPath:"#/$defs/cursor/oneOf/1/type",keyword:"type",params:{type: "null"},message:"must be null"};
if(vErrors === null){
vErrors = [err2];
}
else {
vErrors.push(err2);
}
errors++;
}
var _valid0 = _errs10 === errors;
if(_valid0 && valid3){
valid3 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid3 = true;
passing0 = 1;
}
}
if(!valid3){
const err3 = {instancePath:instancePath+"/nextCursor",schemaPath:"#/$defs/cursor/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err3];
}
else {
vErrors.push(err3);
}
errors++;
validate22.errors = vErrors;
return false;
}
else {
errors = _errs7;
if(vErrors !== null){
if(_errs7){
vErrors.length = _errs7;
}
else {
vErrors = null;
}
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.complete !== undefined){
const _errs12 = errors;
if(typeof data.complete !== "boolean"){
validate22.errors = [{instancePath:instancePath+"/complete",schemaPath:"#/properties/complete/type",keyword:"type",params:{type: "boolean"},message:"must be boolean"}];
return false;
}
var valid0 = _errs12 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.items !== undefined){
let data3 = data.items;
const _errs14 = errors;
if(errors === _errs14){
if(Array.isArray(data3)){
var valid4 = true;
const len0 = data3.length;
for(let i0=0; i0<len0; i0++){
const _errs16 = errors;
if(!(validate23(data3[i0], {instancePath:instancePath+"/items/" + i0,parentData:data3,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate23.errors : vErrors.concat(validate23.errors);
errors = vErrors.length;
}
var valid4 = _errs16 === errors;
if(!valid4){
break;
}
}
}
else {
validate22.errors = [{instancePath:instancePath+"/items",schemaPath:"#/properties/items/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs14 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
else {
validate22.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate22.errors = vErrors;
return errors === 0;
}
validate22.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema55 = {"type":"object","additionalProperties":false,"properties":{"revisionKey":{"$ref":"#/$defs/revisionKey"},"revisionDigest":{"$ref":"#/$defs/digest"},"bodyDigest":{"$ref":"#/$defs/digest"},"structure":{"$ref":"#/$defs/structure"},"body":{"type":"string"},"proposedBy":{"$ref":"#/$defs/uuid"}},"required":["revisionKey","revisionDigest","bodyDigest","structure","body","proposedBy"]};
const schema58 = {"type":"object","additionalProperties":false,"required":["clauses","context"],"properties":{"clauses":{"type":"array","items":{"$ref":"#/$defs/clauseSpan"}},"context":{"type":"array","items":{"$ref":"#/$defs/range"}}}};
const schema59 = {"type":"object","additionalProperties":false,"required":["id","span"],"properties":{"id":{"$ref":"#/$defs/clauseId"},"span":{"$ref":"#/$defs/range"}}};
const schema60 = {"type":"string","minLength":1};
const func1 = require("ajv/dist/runtime/ucs2length").default;
const schema61 = {"type":"object","additionalProperties":false,"required":["start","endExclusive"],"properties":{"start":{"$ref":"#/$defs/offset"},"endExclusive":{"$ref":"#/$defs/offset"}}};
const schema62 = {"type":"integer","minimum":0,"maximum":2147483647};

function validate39(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate39.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.start === undefined) && (missing0 = "start")) || ((data.endExclusive === undefined) && (missing0 = "endExclusive"))){
validate39.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "start") || (key0 === "endExclusive"))){
validate39.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.start !== undefined){
let data0 = data.start;
const _errs2 = errors;
const _errs3 = errors;
if(!((typeof data0 == "number") && (!(data0 % 1) && !isNaN(data0)))){
validate39.errors = [{instancePath:instancePath+"/start",schemaPath:"#/$defs/offset/type",keyword:"type",params:{type: "integer"},message:"must be integer"}];
return false;
}
if(errors === _errs3){
if(typeof data0 == "number"){
if(data0 > 2147483647 || isNaN(data0)){
validate39.errors = [{instancePath:instancePath+"/start",schemaPath:"#/$defs/offset/maximum",keyword:"maximum",params:{comparison: "<=", limit: 2147483647},message:"must be <= 2147483647"}];
return false;
}
else {
if(data0 < 0 || isNaN(data0)){
validate39.errors = [{instancePath:instancePath+"/start",schemaPath:"#/$defs/offset/minimum",keyword:"minimum",params:{comparison: ">=", limit: 0},message:"must be >= 0"}];
return false;
}
}
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.endExclusive !== undefined){
let data1 = data.endExclusive;
const _errs5 = errors;
const _errs6 = errors;
if(!((typeof data1 == "number") && (!(data1 % 1) && !isNaN(data1)))){
validate39.errors = [{instancePath:instancePath+"/endExclusive",schemaPath:"#/$defs/offset/type",keyword:"type",params:{type: "integer"},message:"must be integer"}];
return false;
}
if(errors === _errs6){
if(typeof data1 == "number"){
if(data1 > 2147483647 || isNaN(data1)){
validate39.errors = [{instancePath:instancePath+"/endExclusive",schemaPath:"#/$defs/offset/maximum",keyword:"maximum",params:{comparison: "<=", limit: 2147483647},message:"must be <= 2147483647"}];
return false;
}
else {
if(data1 < 0 || isNaN(data1)){
validate39.errors = [{instancePath:instancePath+"/endExclusive",schemaPath:"#/$defs/offset/minimum",keyword:"minimum",params:{comparison: ">=", limit: 0},message:"must be >= 0"}];
return false;
}
}
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate39.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate39.errors = vErrors;
return errors === 0;
}
validate39.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate38(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate38.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.id === undefined) && (missing0 = "id")) || ((data.span === undefined) && (missing0 = "span"))){
validate38.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "id") || (key0 === "span"))){
validate38.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.id !== undefined){
let data0 = data.id;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(func1(data0) < 1){
validate38.errors = [{instancePath:instancePath+"/id",schemaPath:"#/$defs/clauseId/minLength",keyword:"minLength",params:{limit: 1},message:"must NOT have fewer than 1 characters"}];
return false;
}
}
else {
validate38.errors = [{instancePath:instancePath+"/id",schemaPath:"#/$defs/clauseId/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.span !== undefined){
const _errs5 = errors;
if(!(validate39(data.span, {instancePath:instancePath+"/span",parentData:data,parentDataProperty:"span",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate39.errors : vErrors.concat(validate39.errors);
errors = vErrors.length;
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate38.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate38.errors = vErrors;
return errors === 0;
}
validate38.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate37(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate37.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.clauses === undefined) && (missing0 = "clauses")) || ((data.context === undefined) && (missing0 = "context"))){
validate37.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "clauses") || (key0 === "context"))){
validate37.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.clauses !== undefined){
let data0 = data.clauses;
const _errs2 = errors;
if(errors === _errs2){
if(Array.isArray(data0)){
var valid1 = true;
const len0 = data0.length;
for(let i0=0; i0<len0; i0++){
const _errs4 = errors;
if(!(validate38(data0[i0], {instancePath:instancePath+"/clauses/" + i0,parentData:data0,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate38.errors : vErrors.concat(validate38.errors);
errors = vErrors.length;
}
var valid1 = _errs4 === errors;
if(!valid1){
break;
}
}
}
else {
validate37.errors = [{instancePath:instancePath+"/clauses",schemaPath:"#/properties/clauses/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.context !== undefined){
let data2 = data.context;
const _errs5 = errors;
if(errors === _errs5){
if(Array.isArray(data2)){
var valid2 = true;
const len1 = data2.length;
for(let i1=0; i1<len1; i1++){
const _errs7 = errors;
if(!(validate39(data2[i1], {instancePath:instancePath+"/context/" + i1,parentData:data2,parentDataProperty:i1,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate39.errors : vErrors.concat(validate39.errors);
errors = vErrors.length;
}
var valid2 = _errs7 === errors;
if(!valid2){
break;
}
}
}
else {
validate37.errors = [{instancePath:instancePath+"/context",schemaPath:"#/properties/context/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate37.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate37.errors = vErrors;
return errors === 0;
}
validate37.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate35(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate35.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((((data.revisionKey === undefined) && (missing0 = "revisionKey")) || ((data.revisionDigest === undefined) && (missing0 = "revisionDigest"))) || ((data.bodyDigest === undefined) && (missing0 = "bodyDigest"))) || ((data.structure === undefined) && (missing0 = "structure"))) || ((data.body === undefined) && (missing0 = "body"))) || ((data.proposedBy === undefined) && (missing0 = "proposedBy"))){
validate35.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((((key0 === "revisionKey") || (key0 === "revisionDigest")) || (key0 === "bodyDigest")) || (key0 === "structure")) || (key0 === "body")) || (key0 === "proposedBy"))){
validate35.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.revisionKey !== undefined){
const _errs2 = errors;
if(!(validate26(data.revisionKey, {instancePath:instancePath+"/revisionKey",parentData:data,parentDataProperty:"revisionKey",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate26.errors : vErrors.concat(validate26.errors);
errors = vErrors.length;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.revisionDigest !== undefined){
let data1 = data.revisionDigest;
const _errs3 = errors;
const _errs4 = errors;
if(errors === _errs4){
if(typeof data1 === "string"){
if(!pattern5.test(data1)){
validate35.errors = [{instancePath:instancePath+"/revisionDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate35.errors = [{instancePath:instancePath+"/revisionDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.bodyDigest !== undefined){
let data2 = data.bodyDigest;
const _errs6 = errors;
const _errs7 = errors;
if(errors === _errs7){
if(typeof data2 === "string"){
if(!pattern5.test(data2)){
validate35.errors = [{instancePath:instancePath+"/bodyDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate35.errors = [{instancePath:instancePath+"/bodyDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs6 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.structure !== undefined){
const _errs9 = errors;
if(!(validate37(data.structure, {instancePath:instancePath+"/structure",parentData:data,parentDataProperty:"structure",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate37.errors : vErrors.concat(validate37.errors);
errors = vErrors.length;
}
var valid0 = _errs9 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.body !== undefined){
const _errs10 = errors;
if(typeof data.body !== "string"){
validate35.errors = [{instancePath:instancePath+"/body",schemaPath:"#/properties/body/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
var valid0 = _errs10 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.proposedBy !== undefined){
let data5 = data.proposedBy;
const _errs12 = errors;
const _errs13 = errors;
if(errors === _errs13){
if(typeof data5 === "string"){
if(!pattern6.test(data5)){
validate35.errors = [{instancePath:instancePath+"/proposedBy",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate35.errors = [{instancePath:instancePath+"/proposedBy",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs12 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
}
}
else {
validate35.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate35.errors = vErrors;
return errors === 0;
}
validate35.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema66 = {"type":"object","additionalProperties":false,"properties":{"reference":{"$ref":"#/$defs/reference"},"fragments":{"type":"array","items":{"type":"object","additionalProperties":false,"properties":{"span":{"$ref":"#/$defs/range"},"text":{"type":"string"}},"required":["span","text"]}},"observation":{"type":"string"},"trace":{"oneOf":[{"$ref":"#/$defs/trace"},{"type":"null"}]}},"required":["reference","fragments","observation","trace"]};
const schema67 = {"oneOf":[{"$ref":"#/$defs/clauseReference"},{"$ref":"#/$defs/contextReference"}]};
const schema68 = {"type":"object","additionalProperties":false,"required":["kind","revision","revisionDigest","clauseId","contentDigest"],"properties":{"kind":{"const":"clause"},"revision":{"$ref":"#/$defs/revisionKey"},"revisionDigest":{"$ref":"#/$defs/digest"},"clauseId":{"$ref":"#/$defs/clauseId"},"contentDigest":{"$ref":"#/$defs/digest"}}};

function validate47(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate47.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if((((((data.kind === undefined) && (missing0 = "kind")) || ((data.revision === undefined) && (missing0 = "revision"))) || ((data.revisionDigest === undefined) && (missing0 = "revisionDigest"))) || ((data.clauseId === undefined) && (missing0 = "clauseId"))) || ((data.contentDigest === undefined) && (missing0 = "contentDigest"))){
validate47.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!(((((key0 === "kind") || (key0 === "revision")) || (key0 === "revisionDigest")) || (key0 === "clauseId")) || (key0 === "contentDigest"))){
validate47.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.kind !== undefined){
const _errs2 = errors;
if("clause" !== data.kind){
validate47.errors = [{instancePath:instancePath+"/kind",schemaPath:"#/properties/kind/const",keyword:"const",params:{allowedValue: "clause"},message:"must be equal to constant"}];
return false;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.revision !== undefined){
const _errs3 = errors;
if(!(validate26(data.revision, {instancePath:instancePath+"/revision",parentData:data,parentDataProperty:"revision",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate26.errors : vErrors.concat(validate26.errors);
errors = vErrors.length;
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.revisionDigest !== undefined){
let data2 = data.revisionDigest;
const _errs4 = errors;
const _errs5 = errors;
if(errors === _errs5){
if(typeof data2 === "string"){
if(!pattern5.test(data2)){
validate47.errors = [{instancePath:instancePath+"/revisionDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate47.errors = [{instancePath:instancePath+"/revisionDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs4 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.clauseId !== undefined){
let data3 = data.clauseId;
const _errs7 = errors;
const _errs8 = errors;
if(errors === _errs8){
if(typeof data3 === "string"){
if(func1(data3) < 1){
validate47.errors = [{instancePath:instancePath+"/clauseId",schemaPath:"#/$defs/clauseId/minLength",keyword:"minLength",params:{limit: 1},message:"must NOT have fewer than 1 characters"}];
return false;
}
}
else {
validate47.errors = [{instancePath:instancePath+"/clauseId",schemaPath:"#/$defs/clauseId/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs7 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.contentDigest !== undefined){
let data4 = data.contentDigest;
const _errs10 = errors;
const _errs11 = errors;
if(errors === _errs11){
if(typeof data4 === "string"){
if(!pattern5.test(data4)){
validate47.errors = [{instancePath:instancePath+"/contentDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate47.errors = [{instancePath:instancePath+"/contentDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs10 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
}
else {
validate47.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate47.errors = vErrors;
return errors === 0;
}
validate47.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema72 = {"type":"object","additionalProperties":false,"required":["kind","revision","revisionDigest","contextDigest"],"properties":{"kind":{"const":"context"},"revision":{"$ref":"#/$defs/revisionKey"},"revisionDigest":{"$ref":"#/$defs/digest"},"contextDigest":{"$ref":"#/$defs/digest"}}};

function validate50(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate50.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((data.kind === undefined) && (missing0 = "kind")) || ((data.revision === undefined) && (missing0 = "revision"))) || ((data.revisionDigest === undefined) && (missing0 = "revisionDigest"))) || ((data.contextDigest === undefined) && (missing0 = "contextDigest"))){
validate50.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((key0 === "kind") || (key0 === "revision")) || (key0 === "revisionDigest")) || (key0 === "contextDigest"))){
validate50.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.kind !== undefined){
const _errs2 = errors;
if("context" !== data.kind){
validate50.errors = [{instancePath:instancePath+"/kind",schemaPath:"#/properties/kind/const",keyword:"const",params:{allowedValue: "context"},message:"must be equal to constant"}];
return false;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.revision !== undefined){
const _errs3 = errors;
if(!(validate26(data.revision, {instancePath:instancePath+"/revision",parentData:data,parentDataProperty:"revision",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate26.errors : vErrors.concat(validate26.errors);
errors = vErrors.length;
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.revisionDigest !== undefined){
let data2 = data.revisionDigest;
const _errs4 = errors;
const _errs5 = errors;
if(errors === _errs5){
if(typeof data2 === "string"){
if(!pattern5.test(data2)){
validate50.errors = [{instancePath:instancePath+"/revisionDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate50.errors = [{instancePath:instancePath+"/revisionDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs4 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.contextDigest !== undefined){
let data3 = data.contextDigest;
const _errs7 = errors;
const _errs8 = errors;
if(errors === _errs8){
if(typeof data3 === "string"){
if(!pattern5.test(data3)){
validate50.errors = [{instancePath:instancePath+"/contextDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate50.errors = [{instancePath:instancePath+"/contextDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs7 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
else {
validate50.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate50.errors = vErrors;
return errors === 0;
}
validate50.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate46(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate46.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
const _errs0 = errors;
let valid0 = false;
let passing0 = null;
const _errs1 = errors;
if(!(validate47(data, {instancePath,parentData,parentDataProperty,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate47.errors : vErrors.concat(validate47.errors);
errors = vErrors.length;
}
var _valid0 = _errs1 === errors;
if(_valid0){
valid0 = true;
passing0 = 0;
var props0 = true;
}
const _errs2 = errors;
if(!(validate50(data, {instancePath,parentData,parentDataProperty,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate50.errors : vErrors.concat(validate50.errors);
errors = vErrors.length;
}
var _valid0 = _errs2 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid0 = true;
passing0 = 1;
if(props0 !== true){
props0 = true;
}
}
}
if(!valid0){
const err0 = {instancePath,schemaPath:"#/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
validate46.errors = vErrors;
return false;
}
else {
errors = _errs0;
if(vErrors !== null){
if(_errs0){
vErrors.length = _errs0;
}
else {
vErrors = null;
}
}
}
validate46.errors = vErrors;
evaluated0.props = props0;
return errors === 0;
}
validate46.evaluated = {"dynamicProps":true,"dynamicItems":false};

const schema75 = {"type":"object","additionalProperties":false,"required":["origin","requestId","approvedBy","assessmentIds"],"properties":{"origin":{"$ref":"#/$defs/reference"},"requestId":{"$ref":"#/$defs/uuid"},"approvedBy":{"$ref":"#/$defs/uuid"},"assessmentIds":{"type":"array","items":{"$ref":"#/$defs/uuid"}}}};

function validate55(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate55.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((data.origin === undefined) && (missing0 = "origin")) || ((data.requestId === undefined) && (missing0 = "requestId"))) || ((data.approvedBy === undefined) && (missing0 = "approvedBy"))) || ((data.assessmentIds === undefined) && (missing0 = "assessmentIds"))){
validate55.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((key0 === "origin") || (key0 === "requestId")) || (key0 === "approvedBy")) || (key0 === "assessmentIds"))){
validate55.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.origin !== undefined){
const _errs2 = errors;
if(!(validate46(data.origin, {instancePath:instancePath+"/origin",parentData:data,parentDataProperty:"origin",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.requestId !== undefined){
let data1 = data.requestId;
const _errs3 = errors;
const _errs4 = errors;
if(errors === _errs4){
if(typeof data1 === "string"){
if(!pattern6.test(data1)){
validate55.errors = [{instancePath:instancePath+"/requestId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate55.errors = [{instancePath:instancePath+"/requestId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.approvedBy !== undefined){
let data2 = data.approvedBy;
const _errs6 = errors;
const _errs7 = errors;
if(errors === _errs7){
if(typeof data2 === "string"){
if(!pattern6.test(data2)){
validate55.errors = [{instancePath:instancePath+"/approvedBy",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate55.errors = [{instancePath:instancePath+"/approvedBy",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs6 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.assessmentIds !== undefined){
let data3 = data.assessmentIds;
const _errs9 = errors;
if(errors === _errs9){
if(Array.isArray(data3)){
var valid3 = true;
const len0 = data3.length;
for(let i0=0; i0<len0; i0++){
let data4 = data3[i0];
const _errs11 = errors;
const _errs12 = errors;
if(errors === _errs12){
if(typeof data4 === "string"){
if(!pattern6.test(data4)){
validate55.errors = [{instancePath:instancePath+"/assessmentIds/" + i0,schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate55.errors = [{instancePath:instancePath+"/assessmentIds/" + i0,schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid3 = _errs11 === errors;
if(!valid3){
break;
}
}
}
else {
validate55.errors = [{instancePath:instancePath+"/assessmentIds",schemaPath:"#/properties/assessmentIds/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs9 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
else {
validate55.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate55.errors = vErrors;
return errors === 0;
}
validate55.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate45(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate45.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((data.reference === undefined) && (missing0 = "reference")) || ((data.fragments === undefined) && (missing0 = "fragments"))) || ((data.observation === undefined) && (missing0 = "observation"))) || ((data.trace === undefined) && (missing0 = "trace"))){
validate45.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((key0 === "reference") || (key0 === "fragments")) || (key0 === "observation")) || (key0 === "trace"))){
validate45.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.reference !== undefined){
const _errs2 = errors;
if(!(validate46(data.reference, {instancePath:instancePath+"/reference",parentData:data,parentDataProperty:"reference",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.fragments !== undefined){
let data1 = data.fragments;
const _errs3 = errors;
if(errors === _errs3){
if(Array.isArray(data1)){
var valid1 = true;
const len0 = data1.length;
for(let i0=0; i0<len0; i0++){
let data2 = data1[i0];
const _errs5 = errors;
if(errors === _errs5){
if(data2 && typeof data2 == "object" && !Array.isArray(data2)){
let missing1;
if(((data2.span === undefined) && (missing1 = "span")) || ((data2.text === undefined) && (missing1 = "text"))){
validate45.errors = [{instancePath:instancePath+"/fragments/" + i0,schemaPath:"#/properties/fragments/items/required",keyword:"required",params:{missingProperty: missing1},message:"must have required property '"+missing1+"'"}];
return false;
}
else {
const _errs7 = errors;
for(const key1 in data2){
if(!((key1 === "span") || (key1 === "text"))){
validate45.errors = [{instancePath:instancePath+"/fragments/" + i0,schemaPath:"#/properties/fragments/items/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key1},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs7 === errors){
if(data2.span !== undefined){
const _errs8 = errors;
if(!(validate39(data2.span, {instancePath:instancePath+"/fragments/" + i0+"/span",parentData:data2,parentDataProperty:"span",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate39.errors : vErrors.concat(validate39.errors);
errors = vErrors.length;
}
var valid2 = _errs8 === errors;
}
else {
var valid2 = true;
}
if(valid2){
if(data2.text !== undefined){
const _errs9 = errors;
if(typeof data2.text !== "string"){
validate45.errors = [{instancePath:instancePath+"/fragments/" + i0+"/text",schemaPath:"#/properties/fragments/items/properties/text/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
var valid2 = _errs9 === errors;
}
else {
var valid2 = true;
}
}
}
}
}
else {
validate45.errors = [{instancePath:instancePath+"/fragments/" + i0,schemaPath:"#/properties/fragments/items/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
var valid1 = _errs5 === errors;
if(!valid1){
break;
}
}
}
else {
validate45.errors = [{instancePath:instancePath+"/fragments",schemaPath:"#/properties/fragments/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.observation !== undefined){
const _errs11 = errors;
if(typeof data.observation !== "string"){
validate45.errors = [{instancePath:instancePath+"/observation",schemaPath:"#/properties/observation/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
var valid0 = _errs11 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.trace !== undefined){
let data6 = data.trace;
const _errs13 = errors;
const _errs14 = errors;
let valid3 = false;
let passing0 = null;
const _errs15 = errors;
if(!(validate55(data6, {instancePath:instancePath+"/trace",parentData:data,parentDataProperty:"trace",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate55.errors : vErrors.concat(validate55.errors);
errors = vErrors.length;
}
var _valid0 = _errs15 === errors;
if(_valid0){
valid3 = true;
passing0 = 0;
}
const _errs16 = errors;
if(data6 !== null){
const err0 = {instancePath:instancePath+"/trace",schemaPath:"#/properties/trace/oneOf/1/type",keyword:"type",params:{type: "null"},message:"must be null"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
var _valid0 = _errs16 === errors;
if(_valid0 && valid3){
valid3 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid3 = true;
passing0 = 1;
}
}
if(!valid3){
const err1 = {instancePath:instancePath+"/trace",schemaPath:"#/properties/trace/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
validate45.errors = vErrors;
return false;
}
else {
errors = _errs14;
if(vErrors !== null){
if(_errs14){
vErrors.length = _errs14;
}
else {
vErrors = null;
}
}
}
var valid0 = _errs13 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
else {
validate45.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate45.errors = vErrors;
return errors === 0;
}
validate45.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema81 = {"type":"object","additionalProperties":false,"required":["formatVersion","family","key","payload"],"properties":{"formatVersion":{"const":1},"family":{"type":"string"},"key":{},"payload":{}},"oneOf":[{"properties":{"family":{"const":"revisions"},"key":{"$ref":"#/$defs/revisionKey"},"payload":{"$ref":"#/$defs/revisionPayload"}}},{"properties":{"family":{"const":"proposals"},"key":{"$ref":"#/$defs/revisionKey"},"payload":{"$ref":"#/$defs/proposalPayload"}}},{"properties":{"family":{"const":"grants"},"key":{"$ref":"#/$defs/grantKey"},"payload":{"$ref":"#/$defs/grantPayload"}}},{"properties":{"family":{"const":"requests"},"key":{"$ref":"#/$defs/uuid"},"payload":{"$ref":"#/$defs/requestPayload"}}},{"properties":{"family":{"const":"resolutions"},"key":{"$ref":"#/$defs/uuid"},"payload":{"$ref":"#/$defs/resolutionPayload"}}},{"properties":{"family":{"const":"approvals"},"key":{"$ref":"#/$defs/reference"},"payload":{"$ref":"#/$defs/trace"}}},{"properties":{"family":{"const":"continuity"},"key":{"$ref":"#/$defs/reference"},"payload":{"$ref":"#/$defs/continuityPayload"}}},{"properties":{"family":{"const":"assessments"},"key":{"$ref":"#/$defs/uuid"},"payload":{"$ref":"#/$defs/assessmentPayload"}}},{"properties":{"family":{"const":"manifests"},"key":{"$ref":"#/$defs/uuid"},"payload":{"$ref":"#/$defs/manifestPayload"}}},{"properties":{"family":{"const":"heads"},"key":{"$ref":"#/$defs/subject"},"payload":{"$ref":"#/$defs/headPayload"}}},{"properties":{"family":{"const":"generations"},"key":{"$ref":"#/$defs/subject"},"payload":{"$ref":"#/$defs/generationPayload"}}},{"properties":{"family":{"const":"blocked"},"key":{"$ref":"#/$defs/reference"},"payload":{"$ref":"#/$defs/emptyPayload"}}}]};
const schema93 = {"type":"object","additionalProperties":false,"required":["capabilities"],"properties":{"capabilities":{"type":"array","uniqueItems":true,"items":{"enum":["READ","PROPOSE","PLAN","APPROVE","MANAGE"]}}}};
const schema140 = {"type":"object","additionalProperties":false,"maxProperties":0};
const schema82 = {"type":"object","additionalProperties":false,"required":["bodyDigest","bodyBytes","structure","structureDigest","revisionDigest","contextDigest"],"properties":{"bodyDigest":{"$ref":"#/$defs/digest"},"bodyBytes":{"$ref":"#/$defs/offset"},"structure":{"$ref":"#/$defs/structure"},"structureDigest":{"$ref":"#/$defs/digest"},"revisionDigest":{"$ref":"#/$defs/digest"},"contextDigest":{"$ref":"#/$defs/digest"}}};

function validate62(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate62.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((((data.bodyDigest === undefined) && (missing0 = "bodyDigest")) || ((data.bodyBytes === undefined) && (missing0 = "bodyBytes"))) || ((data.structure === undefined) && (missing0 = "structure"))) || ((data.structureDigest === undefined) && (missing0 = "structureDigest"))) || ((data.revisionDigest === undefined) && (missing0 = "revisionDigest"))) || ((data.contextDigest === undefined) && (missing0 = "contextDigest"))){
validate62.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((((key0 === "bodyDigest") || (key0 === "bodyBytes")) || (key0 === "structure")) || (key0 === "structureDigest")) || (key0 === "revisionDigest")) || (key0 === "contextDigest"))){
validate62.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.bodyDigest !== undefined){
let data0 = data.bodyDigest;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern5.test(data0)){
validate62.errors = [{instancePath:instancePath+"/bodyDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate62.errors = [{instancePath:instancePath+"/bodyDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.bodyBytes !== undefined){
let data1 = data.bodyBytes;
const _errs5 = errors;
const _errs6 = errors;
if(!((typeof data1 == "number") && (!(data1 % 1) && !isNaN(data1)))){
validate62.errors = [{instancePath:instancePath+"/bodyBytes",schemaPath:"#/$defs/offset/type",keyword:"type",params:{type: "integer"},message:"must be integer"}];
return false;
}
if(errors === _errs6){
if(typeof data1 == "number"){
if(data1 > 2147483647 || isNaN(data1)){
validate62.errors = [{instancePath:instancePath+"/bodyBytes",schemaPath:"#/$defs/offset/maximum",keyword:"maximum",params:{comparison: "<=", limit: 2147483647},message:"must be <= 2147483647"}];
return false;
}
else {
if(data1 < 0 || isNaN(data1)){
validate62.errors = [{instancePath:instancePath+"/bodyBytes",schemaPath:"#/$defs/offset/minimum",keyword:"minimum",params:{comparison: ">=", limit: 0},message:"must be >= 0"}];
return false;
}
}
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.structure !== undefined){
const _errs8 = errors;
if(!(validate37(data.structure, {instancePath:instancePath+"/structure",parentData:data,parentDataProperty:"structure",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate37.errors : vErrors.concat(validate37.errors);
errors = vErrors.length;
}
var valid0 = _errs8 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.structureDigest !== undefined){
let data3 = data.structureDigest;
const _errs9 = errors;
const _errs10 = errors;
if(errors === _errs10){
if(typeof data3 === "string"){
if(!pattern5.test(data3)){
validate62.errors = [{instancePath:instancePath+"/structureDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate62.errors = [{instancePath:instancePath+"/structureDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs9 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.revisionDigest !== undefined){
let data4 = data.revisionDigest;
const _errs12 = errors;
const _errs13 = errors;
if(errors === _errs13){
if(typeof data4 === "string"){
if(!pattern5.test(data4)){
validate62.errors = [{instancePath:instancePath+"/revisionDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate62.errors = [{instancePath:instancePath+"/revisionDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs12 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.contextDigest !== undefined){
let data5 = data.contextDigest;
const _errs15 = errors;
const _errs16 = errors;
if(errors === _errs16){
if(typeof data5 === "string"){
if(!pattern5.test(data5)){
validate62.errors = [{instancePath:instancePath+"/contextDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate62.errors = [{instancePath:instancePath+"/contextDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs15 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
}
}
else {
validate62.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate62.errors = vErrors;
return errors === 0;
}
validate62.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema88 = {"type":"object","additionalProperties":false,"required":["principalId"],"properties":{"principalId":{"$ref":"#/$defs/uuid"}}};

function validate66(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate66.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if((data.principalId === undefined) && (missing0 = "principalId")){
validate66.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!(key0 === "principalId")){
validate66.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.principalId !== undefined){
let data0 = data.principalId;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate66.errors = [{instancePath:instancePath+"/principalId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate66.errors = [{instancePath:instancePath+"/principalId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
}
}
}
}
else {
validate66.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate66.errors = vErrors;
return errors === 0;
}
validate66.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema90 = {"type":"object","additionalProperties":false,"required":["principalId","projectId"],"properties":{"principalId":{"$ref":"#/$defs/uuid"},"projectId":{"$ref":"#/$defs/uuid"}}};

function validate68(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate68.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.principalId === undefined) && (missing0 = "principalId")) || ((data.projectId === undefined) && (missing0 = "projectId"))){
validate68.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "principalId") || (key0 === "projectId"))){
validate68.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.principalId !== undefined){
let data0 = data.principalId;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate68.errors = [{instancePath:instancePath+"/principalId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate68.errors = [{instancePath:instancePath+"/principalId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.projectId !== undefined){
let data1 = data.projectId;
const _errs5 = errors;
const _errs6 = errors;
if(errors === _errs6){
if(typeof data1 === "string"){
if(!pattern6.test(data1)){
validate68.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate68.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate68.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate68.errors = vErrors;
return errors === 0;
}
validate68.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema95 = {"type":"object","additionalProperties":false,"required":["projectId","proposedBy","packet","generations","packetDigest","requestDigest"],"properties":{"projectId":{"$ref":"#/$defs/uuid"},"proposedBy":{"$ref":"#/$defs/uuid"},"packet":{"$ref":"#/$defs/packet"},"generations":{"type":"array","items":{"$ref":"#/$defs/guard"}},"packetDigest":{"$ref":"#/$defs/digest"},"requestDigest":{"$ref":"#/$defs/digest"}}};
const schema98 = {"type":"object","additionalProperties":false,"required":["items","question","scope","rationale","consequences","alternatives","uncertainties","contexts","dependencies","informationGaps"],"properties":{"items":{"type":"array","minItems":1,"items":{"$ref":"#/$defs/reviewItem"}},"question":{"$ref":"#/$defs/text"},"scope":{"$ref":"#/$defs/text"},"rationale":{"$ref":"#/$defs/text"},"consequences":{"allOf":[{"$ref":"#/$defs/texts"},{"minItems":1}]},"alternatives":{"allOf":[{"$ref":"#/$defs/texts"},{"minItems":1}]},"uncertainties":{"$ref":"#/$defs/texts"},"contexts":{"$ref":"#/$defs/contexts"},"dependencies":{"$ref":"#/$defs/references"},"informationGaps":{"$ref":"#/$defs/texts"}}};
const schema101 = {"type":"string","minLength":1};
const func4 = Object.prototype.hasOwnProperty;
const schema99 = {"type":"object","additionalProperties":false,"required":["id","target","before"],"properties":{"id":{"$ref":"#/$defs/uuid"},"target":{"$ref":"#/$defs/reference"},"before":{"oneOf":[{"$ref":"#/$defs/reference"},{"type":"null"}]}}};

function validate72(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate72.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if((((data.id === undefined) && (missing0 = "id")) || ((data.target === undefined) && (missing0 = "target"))) || ((data.before === undefined) && (missing0 = "before"))){
validate72.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!(((key0 === "id") || (key0 === "target")) || (key0 === "before"))){
validate72.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.id !== undefined){
let data0 = data.id;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate72.errors = [{instancePath:instancePath+"/id",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate72.errors = [{instancePath:instancePath+"/id",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.target !== undefined){
const _errs5 = errors;
if(!(validate46(data.target, {instancePath:instancePath+"/target",parentData:data,parentDataProperty:"target",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.before !== undefined){
let data2 = data.before;
const _errs6 = errors;
const _errs7 = errors;
let valid2 = false;
let passing0 = null;
const _errs8 = errors;
if(!(validate46(data2, {instancePath:instancePath+"/before",parentData:data,parentDataProperty:"before",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var _valid0 = _errs8 === errors;
if(_valid0){
valid2 = true;
passing0 = 0;
}
const _errs9 = errors;
if(data2 !== null){
const err0 = {instancePath:instancePath+"/before",schemaPath:"#/properties/before/oneOf/1/type",keyword:"type",params:{type: "null"},message:"must be null"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
var _valid0 = _errs9 === errors;
if(_valid0 && valid2){
valid2 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid2 = true;
passing0 = 1;
}
}
if(!valid2){
const err1 = {instancePath:instancePath+"/before",schemaPath:"#/properties/before/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
validate72.errors = vErrors;
return false;
}
else {
errors = _errs7;
if(vErrors !== null){
if(_errs7){
vErrors.length = _errs7;
}
else {
vErrors = null;
}
}
}
var valid0 = _errs6 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
else {
validate72.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate72.errors = vErrors;
return errors === 0;
}
validate72.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema104 = {"type":"array","items":{"$ref":"#/$defs/text"}};

function validate76(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate76.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(Array.isArray(data)){
var valid0 = true;
const len0 = data.length;
for(let i0=0; i0<len0; i0++){
let data0 = data[i0];
const _errs1 = errors;
const _errs2 = errors;
if(errors === _errs2){
if(typeof data0 === "string"){
if(func1(data0) < 1){
validate76.errors = [{instancePath:instancePath+"/" + i0,schemaPath:"#/$defs/text/minLength",keyword:"minLength",params:{limit: 1},message:"must NOT have fewer than 1 characters"}];
return false;
}
}
else {
validate76.errors = [{instancePath:instancePath+"/" + i0,schemaPath:"#/$defs/text/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs1 === errors;
if(!valid0){
break;
}
}
}
else {
validate76.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
validate76.errors = vErrors;
return errors === 0;
}
validate76.evaluated = {"items":true,"dynamicProps":false,"dynamicItems":false};

const schema106 = {"type":"array","uniqueItems":true,"items":{"$ref":"#/$defs/contextReference"}};
const func0 = require("ajv/dist/runtime/equal").default;

function validate80(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate80.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(Array.isArray(data)){
var valid0 = true;
const len0 = data.length;
for(let i0=0; i0<len0; i0++){
const _errs1 = errors;
if(!(validate50(data[i0], {instancePath:instancePath+"/" + i0,parentData:data,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate50.errors : vErrors.concat(validate50.errors);
errors = vErrors.length;
}
var valid0 = _errs1 === errors;
if(!valid0){
break;
}
}
if(valid0){
let i1 = data.length;
let j0;
if(i1 > 1){
outer0:
for(;i1--;){
for(j0 = i1; j0--;){
if(func0(data[i1], data[j0])){
validate80.errors = [{instancePath,schemaPath:"#/uniqueItems",keyword:"uniqueItems",params:{i: i1, j: j0},message:"must NOT have duplicate items (items ## "+j0+" and "+i1+" are identical)"}];
return false;
break outer0;
}
}
}
}
}
}
else {
validate80.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
validate80.errors = vErrors;
return errors === 0;
}
validate80.evaluated = {"items":true,"dynamicProps":false,"dynamicItems":false};

const schema107 = {"type":"array","uniqueItems":true,"items":{"$ref":"#/$defs/reference"}};

function validate83(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate83.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(Array.isArray(data)){
var valid0 = true;
const len0 = data.length;
for(let i0=0; i0<len0; i0++){
const _errs1 = errors;
if(!(validate46(data[i0], {instancePath:instancePath+"/" + i0,parentData:data,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid0 = _errs1 === errors;
if(!valid0){
break;
}
}
if(valid0){
let i1 = data.length;
let j0;
if(i1 > 1){
outer0:
for(;i1--;){
for(j0 = i1; j0--;){
if(func0(data[i1], data[j0])){
validate83.errors = [{instancePath,schemaPath:"#/uniqueItems",keyword:"uniqueItems",params:{i: i1, j: j0},message:"must NOT have duplicate items (items ## "+j0+" and "+i1+" are identical)"}];
return false;
break outer0;
}
}
}
}
}
}
else {
validate83.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
validate83.errors = vErrors;
return errors === 0;
}
validate83.evaluated = {"items":true,"dynamicProps":false,"dynamicItems":false};


function validate71(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate71.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((((((((data.items === undefined) && (missing0 = "items")) || ((data.question === undefined) && (missing0 = "question"))) || ((data.scope === undefined) && (missing0 = "scope"))) || ((data.rationale === undefined) && (missing0 = "rationale"))) || ((data.consequences === undefined) && (missing0 = "consequences"))) || ((data.alternatives === undefined) && (missing0 = "alternatives"))) || ((data.uncertainties === undefined) && (missing0 = "uncertainties"))) || ((data.contexts === undefined) && (missing0 = "contexts"))) || ((data.dependencies === undefined) && (missing0 = "dependencies"))) || ((data.informationGaps === undefined) && (missing0 = "informationGaps"))){
validate71.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!(func4.call(schema98.properties, key0))){
validate71.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.items !== undefined){
let data0 = data.items;
const _errs2 = errors;
if(errors === _errs2){
if(Array.isArray(data0)){
if(data0.length < 1){
validate71.errors = [{instancePath:instancePath+"/items",schemaPath:"#/properties/items/minItems",keyword:"minItems",params:{limit: 1},message:"must NOT have fewer than 1 items"}];
return false;
}
else {
var valid1 = true;
const len0 = data0.length;
for(let i0=0; i0<len0; i0++){
const _errs4 = errors;
if(!(validate72(data0[i0], {instancePath:instancePath+"/items/" + i0,parentData:data0,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate72.errors : vErrors.concat(validate72.errors);
errors = vErrors.length;
}
var valid1 = _errs4 === errors;
if(!valid1){
break;
}
}
}
}
else {
validate71.errors = [{instancePath:instancePath+"/items",schemaPath:"#/properties/items/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.question !== undefined){
let data2 = data.question;
const _errs5 = errors;
const _errs6 = errors;
if(errors === _errs6){
if(typeof data2 === "string"){
if(func1(data2) < 1){
validate71.errors = [{instancePath:instancePath+"/question",schemaPath:"#/$defs/text/minLength",keyword:"minLength",params:{limit: 1},message:"must NOT have fewer than 1 characters"}];
return false;
}
}
else {
validate71.errors = [{instancePath:instancePath+"/question",schemaPath:"#/$defs/text/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.scope !== undefined){
let data3 = data.scope;
const _errs8 = errors;
const _errs9 = errors;
if(errors === _errs9){
if(typeof data3 === "string"){
if(func1(data3) < 1){
validate71.errors = [{instancePath:instancePath+"/scope",schemaPath:"#/$defs/text/minLength",keyword:"minLength",params:{limit: 1},message:"must NOT have fewer than 1 characters"}];
return false;
}
}
else {
validate71.errors = [{instancePath:instancePath+"/scope",schemaPath:"#/$defs/text/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs8 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.rationale !== undefined){
let data4 = data.rationale;
const _errs11 = errors;
const _errs12 = errors;
if(errors === _errs12){
if(typeof data4 === "string"){
if(func1(data4) < 1){
validate71.errors = [{instancePath:instancePath+"/rationale",schemaPath:"#/$defs/text/minLength",keyword:"minLength",params:{limit: 1},message:"must NOT have fewer than 1 characters"}];
return false;
}
}
else {
validate71.errors = [{instancePath:instancePath+"/rationale",schemaPath:"#/$defs/text/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs11 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.consequences !== undefined){
let data5 = data.consequences;
const _errs14 = errors;
const _errs15 = errors;
if(!(validate76(data5, {instancePath:instancePath+"/consequences",parentData:data,parentDataProperty:"consequences",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate76.errors : vErrors.concat(validate76.errors);
errors = vErrors.length;
}
var valid5 = _errs15 === errors;
if(valid5){
const _errs16 = errors;
if(Array.isArray(data5)){
if(data5.length < 1){
validate71.errors = [{instancePath:instancePath+"/consequences",schemaPath:"#/properties/consequences/allOf/1/minItems",keyword:"minItems",params:{limit: 1},message:"must NOT have fewer than 1 items"}];
return false;
}
}
var valid5 = _errs16 === errors;
}
var valid0 = _errs14 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.alternatives !== undefined){
let data6 = data.alternatives;
const _errs17 = errors;
const _errs18 = errors;
if(!(validate76(data6, {instancePath:instancePath+"/alternatives",parentData:data,parentDataProperty:"alternatives",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate76.errors : vErrors.concat(validate76.errors);
errors = vErrors.length;
}
var valid6 = _errs18 === errors;
if(valid6){
const _errs19 = errors;
if(Array.isArray(data6)){
if(data6.length < 1){
validate71.errors = [{instancePath:instancePath+"/alternatives",schemaPath:"#/properties/alternatives/allOf/1/minItems",keyword:"minItems",params:{limit: 1},message:"must NOT have fewer than 1 items"}];
return false;
}
}
var valid6 = _errs19 === errors;
}
var valid0 = _errs17 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.uncertainties !== undefined){
const _errs20 = errors;
if(!(validate76(data.uncertainties, {instancePath:instancePath+"/uncertainties",parentData:data,parentDataProperty:"uncertainties",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate76.errors : vErrors.concat(validate76.errors);
errors = vErrors.length;
}
var valid0 = _errs20 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.contexts !== undefined){
const _errs21 = errors;
if(!(validate80(data.contexts, {instancePath:instancePath+"/contexts",parentData:data,parentDataProperty:"contexts",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate80.errors : vErrors.concat(validate80.errors);
errors = vErrors.length;
}
var valid0 = _errs21 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.dependencies !== undefined){
const _errs22 = errors;
if(!(validate83(data.dependencies, {instancePath:instancePath+"/dependencies",parentData:data,parentDataProperty:"dependencies",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate83.errors : vErrors.concat(validate83.errors);
errors = vErrors.length;
}
var valid0 = _errs22 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.informationGaps !== undefined){
const _errs23 = errors;
if(!(validate76(data.informationGaps, {instancePath:instancePath+"/informationGaps",parentData:data,parentDataProperty:"informationGaps",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate76.errors : vErrors.concat(validate76.errors);
errors = vErrors.length;
}
var valid0 = _errs23 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
}
}
}
}
}
}
else {
validate71.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate71.errors = vErrors;
return errors === 0;
}
validate71.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema108 = {"type":"object","additionalProperties":false,"required":["subject","generation"],"properties":{"subject":{"$ref":"#/$defs/subject"},"generation":{"$ref":"#/$defs/generation"}}};
const schema111 = {"type":"integer","minimum":0,"maximum":9223372036854776000};
const schema109 = {"type":"object","additionalProperties":false,"required":["document","clauseId"],"properties":{"document":{"$ref":"#/$defs/documentKey"},"clauseId":{"oneOf":[{"$ref":"#/$defs/clauseId"},{"type":"null"}]}}};

function validate89(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate89.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.document === undefined) && (missing0 = "document")) || ((data.clauseId === undefined) && (missing0 = "clauseId"))){
validate89.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "document") || (key0 === "clauseId"))){
validate89.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.document !== undefined){
const _errs2 = errors;
if(!(validate24(data.document, {instancePath:instancePath+"/document",parentData:data,parentDataProperty:"document",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate24.errors : vErrors.concat(validate24.errors);
errors = vErrors.length;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.clauseId !== undefined){
let data1 = data.clauseId;
const _errs3 = errors;
const _errs4 = errors;
let valid1 = false;
let passing0 = null;
const _errs5 = errors;
const _errs6 = errors;
if(errors === _errs6){
if(typeof data1 === "string"){
if(func1(data1) < 1){
const err0 = {instancePath:instancePath+"/clauseId",schemaPath:"#/$defs/clauseId/minLength",keyword:"minLength",params:{limit: 1},message:"must NOT have fewer than 1 characters"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
}
else {
const err1 = {instancePath:instancePath+"/clauseId",schemaPath:"#/$defs/clauseId/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
}
}
var _valid0 = _errs5 === errors;
if(_valid0){
valid1 = true;
passing0 = 0;
}
const _errs8 = errors;
if(data1 !== null){
const err2 = {instancePath:instancePath+"/clauseId",schemaPath:"#/properties/clauseId/oneOf/1/type",keyword:"type",params:{type: "null"},message:"must be null"};
if(vErrors === null){
vErrors = [err2];
}
else {
vErrors.push(err2);
}
errors++;
}
var _valid0 = _errs8 === errors;
if(_valid0 && valid1){
valid1 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid1 = true;
passing0 = 1;
}
}
if(!valid1){
const err3 = {instancePath:instancePath+"/clauseId",schemaPath:"#/properties/clauseId/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err3];
}
else {
vErrors.push(err3);
}
errors++;
validate89.errors = vErrors;
return false;
}
else {
errors = _errs4;
if(vErrors !== null){
if(_errs4){
vErrors.length = _errs4;
}
else {
vErrors = null;
}
}
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate89.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate89.errors = vErrors;
return errors === 0;
}
validate89.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate88(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate88.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.subject === undefined) && (missing0 = "subject")) || ((data.generation === undefined) && (missing0 = "generation"))){
validate88.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "subject") || (key0 === "generation"))){
validate88.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.subject !== undefined){
const _errs2 = errors;
if(!(validate89(data.subject, {instancePath:instancePath+"/subject",parentData:data,parentDataProperty:"subject",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate89.errors : vErrors.concat(validate89.errors);
errors = vErrors.length;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.generation !== undefined){
let data1 = data.generation;
const _errs3 = errors;
const _errs4 = errors;
if(!((typeof data1 == "number") && (!(data1 % 1) && !isNaN(data1)))){
validate88.errors = [{instancePath:instancePath+"/generation",schemaPath:"#/$defs/generation/type",keyword:"type",params:{type: "integer"},message:"must be integer"}];
return false;
}
if(errors === _errs4){
if(typeof data1 == "number"){
if(data1 > 9223372036854776000 || isNaN(data1)){
validate88.errors = [{instancePath:instancePath+"/generation",schemaPath:"#/$defs/generation/maximum",keyword:"maximum",params:{comparison: "<=", limit: 9223372036854776000},message:"must be <= 9223372036854776000"}];
return false;
}
else {
if(data1 < 0 || isNaN(data1)){
validate88.errors = [{instancePath:instancePath+"/generation",schemaPath:"#/$defs/generation/minimum",keyword:"minimum",params:{comparison: ">=", limit: 0},message:"must be >= 0"}];
return false;
}
}
}
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate88.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate88.errors = vErrors;
return errors === 0;
}
validate88.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate70(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate70.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((((data.projectId === undefined) && (missing0 = "projectId")) || ((data.proposedBy === undefined) && (missing0 = "proposedBy"))) || ((data.packet === undefined) && (missing0 = "packet"))) || ((data.generations === undefined) && (missing0 = "generations"))) || ((data.packetDigest === undefined) && (missing0 = "packetDigest"))) || ((data.requestDigest === undefined) && (missing0 = "requestDigest"))){
validate70.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((((key0 === "projectId") || (key0 === "proposedBy")) || (key0 === "packet")) || (key0 === "generations")) || (key0 === "packetDigest")) || (key0 === "requestDigest"))){
validate70.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.projectId !== undefined){
let data0 = data.projectId;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate70.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate70.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.proposedBy !== undefined){
let data1 = data.proposedBy;
const _errs5 = errors;
const _errs6 = errors;
if(errors === _errs6){
if(typeof data1 === "string"){
if(!pattern6.test(data1)){
validate70.errors = [{instancePath:instancePath+"/proposedBy",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate70.errors = [{instancePath:instancePath+"/proposedBy",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.packet !== undefined){
const _errs8 = errors;
if(!(validate71(data.packet, {instancePath:instancePath+"/packet",parentData:data,parentDataProperty:"packet",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate71.errors : vErrors.concat(validate71.errors);
errors = vErrors.length;
}
var valid0 = _errs8 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.generations !== undefined){
let data3 = data.generations;
const _errs9 = errors;
if(errors === _errs9){
if(Array.isArray(data3)){
var valid3 = true;
const len0 = data3.length;
for(let i0=0; i0<len0; i0++){
const _errs11 = errors;
if(!(validate88(data3[i0], {instancePath:instancePath+"/generations/" + i0,parentData:data3,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate88.errors : vErrors.concat(validate88.errors);
errors = vErrors.length;
}
var valid3 = _errs11 === errors;
if(!valid3){
break;
}
}
}
else {
validate70.errors = [{instancePath:instancePath+"/generations",schemaPath:"#/properties/generations/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs9 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.packetDigest !== undefined){
let data5 = data.packetDigest;
const _errs12 = errors;
const _errs13 = errors;
if(errors === _errs13){
if(typeof data5 === "string"){
if(!pattern5.test(data5)){
validate70.errors = [{instancePath:instancePath+"/packetDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate70.errors = [{instancePath:instancePath+"/packetDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs12 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.requestDigest !== undefined){
let data6 = data.requestDigest;
const _errs15 = errors;
const _errs16 = errors;
if(errors === _errs16){
if(typeof data6 === "string"){
if(!pattern5.test(data6)){
validate70.errors = [{instancePath:instancePath+"/requestDigest",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""}];
return false;
}
}
else {
validate70.errors = [{instancePath:instancePath+"/requestDigest",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs15 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
}
}
else {
validate70.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate70.errors = vErrors;
return errors === 0;
}
validate70.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema115 = {"type":"object","additionalProperties":false,"required":["actorId","answer","approved"],"properties":{"actorId":{"$ref":"#/$defs/uuid"},"answer":{"$ref":"#/$defs/answer"},"approved":{"$ref":"#/$defs/references"}}};
const schema117 = {"oneOf":[{"type":"object","additionalProperties":false,"required":["kind"],"properties":{"kind":{"enum":["approve","reject"]}}},{"type":"object","additionalProperties":false,"required":["kind","comments"],"properties":{"kind":{"const":"changes-required"},"comments":{"type":"array","minItems":1,"items":{"$ref":"#/$defs/comment"}}}}]};
const schema118 = {"type":"object","additionalProperties":false,"required":["itemId","text"],"properties":{"itemId":{"$ref":"#/$defs/uuid"},"text":{"$ref":"#/$defs/text"}}};

function validate96(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate96.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.itemId === undefined) && (missing0 = "itemId")) || ((data.text === undefined) && (missing0 = "text"))){
validate96.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "itemId") || (key0 === "text"))){
validate96.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.itemId !== undefined){
let data0 = data.itemId;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate96.errors = [{instancePath:instancePath+"/itemId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate96.errors = [{instancePath:instancePath+"/itemId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.text !== undefined){
let data1 = data.text;
const _errs5 = errors;
const _errs6 = errors;
if(errors === _errs6){
if(typeof data1 === "string"){
if(func1(data1) < 1){
validate96.errors = [{instancePath:instancePath+"/text",schemaPath:"#/$defs/text/minLength",keyword:"minLength",params:{limit: 1},message:"must NOT have fewer than 1 characters"}];
return false;
}
}
else {
validate96.errors = [{instancePath:instancePath+"/text",schemaPath:"#/$defs/text/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate96.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate96.errors = vErrors;
return errors === 0;
}
validate96.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate95(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate95.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
const _errs0 = errors;
let valid0 = false;
let passing0 = null;
const _errs1 = errors;
if(errors === _errs1){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if((data.kind === undefined) && (missing0 = "kind")){
const err0 = {instancePath,schemaPath:"#/oneOf/0/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
else {
const _errs3 = errors;
for(const key0 in data){
if(!(key0 === "kind")){
const err1 = {instancePath,schemaPath:"#/oneOf/0/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
break;
}
}
if(_errs3 === errors){
if(data.kind !== undefined){
let data0 = data.kind;
if(!((data0 === "approve") || (data0 === "reject"))){
const err2 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/0/properties/kind/enum",keyword:"enum",params:{allowedValues: schema117.oneOf[0].properties.kind.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err2];
}
else {
vErrors.push(err2);
}
errors++;
}
}
}
}
}
else {
const err3 = {instancePath,schemaPath:"#/oneOf/0/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err3];
}
else {
vErrors.push(err3);
}
errors++;
}
}
var _valid0 = _errs1 === errors;
if(_valid0){
valid0 = true;
passing0 = 0;
var props0 = true;
}
const _errs5 = errors;
if(errors === _errs5){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing1;
if(((data.kind === undefined) && (missing1 = "kind")) || ((data.comments === undefined) && (missing1 = "comments"))){
const err4 = {instancePath,schemaPath:"#/oneOf/1/required",keyword:"required",params:{missingProperty: missing1},message:"must have required property '"+missing1+"'"};
if(vErrors === null){
vErrors = [err4];
}
else {
vErrors.push(err4);
}
errors++;
}
else {
const _errs7 = errors;
for(const key1 in data){
if(!((key1 === "kind") || (key1 === "comments"))){
const err5 = {instancePath,schemaPath:"#/oneOf/1/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key1},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err5];
}
else {
vErrors.push(err5);
}
errors++;
break;
}
}
if(_errs7 === errors){
if(data.kind !== undefined){
const _errs8 = errors;
if("changes-required" !== data.kind){
const err6 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/1/properties/kind/const",keyword:"const",params:{allowedValue: "changes-required"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err6];
}
else {
vErrors.push(err6);
}
errors++;
}
var valid2 = _errs8 === errors;
}
else {
var valid2 = true;
}
if(valid2){
if(data.comments !== undefined){
let data2 = data.comments;
const _errs9 = errors;
if(errors === _errs9){
if(Array.isArray(data2)){
if(data2.length < 1){
const err7 = {instancePath:instancePath+"/comments",schemaPath:"#/oneOf/1/properties/comments/minItems",keyword:"minItems",params:{limit: 1},message:"must NOT have fewer than 1 items"};
if(vErrors === null){
vErrors = [err7];
}
else {
vErrors.push(err7);
}
errors++;
}
else {
var valid3 = true;
const len0 = data2.length;
for(let i0=0; i0<len0; i0++){
const _errs11 = errors;
if(!(validate96(data2[i0], {instancePath:instancePath+"/comments/" + i0,parentData:data2,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate96.errors : vErrors.concat(validate96.errors);
errors = vErrors.length;
}
var valid3 = _errs11 === errors;
if(!valid3){
break;
}
}
}
}
else {
const err8 = {instancePath:instancePath+"/comments",schemaPath:"#/oneOf/1/properties/comments/type",keyword:"type",params:{type: "array"},message:"must be array"};
if(vErrors === null){
vErrors = [err8];
}
else {
vErrors.push(err8);
}
errors++;
}
}
var valid2 = _errs9 === errors;
}
else {
var valid2 = true;
}
}
}
}
}
else {
const err9 = {instancePath,schemaPath:"#/oneOf/1/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err9];
}
else {
vErrors.push(err9);
}
errors++;
}
}
var _valid0 = _errs5 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid0 = true;
passing0 = 1;
if(props0 !== true){
props0 = true;
}
}
}
if(!valid0){
const err10 = {instancePath,schemaPath:"#/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err10];
}
else {
vErrors.push(err10);
}
errors++;
validate95.errors = vErrors;
return false;
}
else {
errors = _errs0;
if(vErrors !== null){
if(_errs0){
vErrors.length = _errs0;
}
else {
vErrors = null;
}
}
}
validate95.errors = vErrors;
evaluated0.props = props0;
return errors === 0;
}
validate95.evaluated = {"dynamicProps":true,"dynamicItems":false};


function validate94(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate94.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if((((data.actorId === undefined) && (missing0 = "actorId")) || ((data.answer === undefined) && (missing0 = "answer"))) || ((data.approved === undefined) && (missing0 = "approved"))){
validate94.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!(((key0 === "actorId") || (key0 === "answer")) || (key0 === "approved"))){
validate94.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.actorId !== undefined){
let data0 = data.actorId;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate94.errors = [{instancePath:instancePath+"/actorId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate94.errors = [{instancePath:instancePath+"/actorId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.answer !== undefined){
const _errs5 = errors;
if(!(validate95(data.answer, {instancePath:instancePath+"/answer",parentData:data,parentDataProperty:"answer",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate95.errors : vErrors.concat(validate95.errors);
errors = vErrors.length;
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.approved !== undefined){
const _errs6 = errors;
if(!(validate83(data.approved, {instancePath:instancePath+"/approved",parentData:data,parentDataProperty:"approved",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate83.errors : vErrors.concat(validate83.errors);
errors = vErrors.length;
}
var valid0 = _errs6 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
else {
validate94.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate94.errors = vErrors;
return errors === 0;
}
validate94.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema121 = {"type":"object","additionalProperties":false,"required":["source","assessmentId"],"properties":{"source":{"$ref":"#/$defs/reference"},"assessmentId":{"$ref":"#/$defs/uuid"}}};

function validate104(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate104.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.source === undefined) && (missing0 = "source")) || ((data.assessmentId === undefined) && (missing0 = "assessmentId"))){
validate104.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "source") || (key0 === "assessmentId"))){
validate104.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.source !== undefined){
const _errs2 = errors;
if(!(validate46(data.source, {instancePath:instancePath+"/source",parentData:data,parentDataProperty:"source",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.assessmentId !== undefined){
let data1 = data.assessmentId;
const _errs3 = errors;
const _errs4 = errors;
if(errors === _errs4){
if(typeof data1 === "string"){
if(!pattern6.test(data1)){
validate104.errors = [{instancePath:instancePath+"/assessmentId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate104.errors = [{instancePath:instancePath+"/assessmentId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate104.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate104.errors = vErrors;
return errors === 0;
}
validate104.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema124 = {"type":"object","additionalProperties":false,"required":["projectId","actorId","references","rationale","links","blocked"],"properties":{"projectId":{"$ref":"#/$defs/uuid"},"actorId":{"$ref":"#/$defs/uuid"},"references":{"$ref":"#/$defs/references"},"rationale":{"$ref":"#/$defs/text"},"links":{"type":"array","items":{"$ref":"#/$defs/link"}},"blocked":{"$ref":"#/$defs/references"}}};
const schema128 = {"type":"object","additionalProperties":false,"required":["source","target"],"properties":{"source":{"$ref":"#/$defs/reference"},"target":{"$ref":"#/$defs/reference"}}};

function validate109(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate109.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.source === undefined) && (missing0 = "source")) || ((data.target === undefined) && (missing0 = "target"))){
validate109.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "source") || (key0 === "target"))){
validate109.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.source !== undefined){
const _errs2 = errors;
if(!(validate46(data.source, {instancePath:instancePath+"/source",parentData:data,parentDataProperty:"source",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.target !== undefined){
const _errs3 = errors;
if(!(validate46(data.target, {instancePath:instancePath+"/target",parentData:data,parentDataProperty:"target",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate109.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate109.errors = vErrors;
return errors === 0;
}
validate109.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate107(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate107.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((((data.projectId === undefined) && (missing0 = "projectId")) || ((data.actorId === undefined) && (missing0 = "actorId"))) || ((data.references === undefined) && (missing0 = "references"))) || ((data.rationale === undefined) && (missing0 = "rationale"))) || ((data.links === undefined) && (missing0 = "links"))) || ((data.blocked === undefined) && (missing0 = "blocked"))){
validate107.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((((key0 === "projectId") || (key0 === "actorId")) || (key0 === "references")) || (key0 === "rationale")) || (key0 === "links")) || (key0 === "blocked"))){
validate107.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.projectId !== undefined){
let data0 = data.projectId;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate107.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate107.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.actorId !== undefined){
let data1 = data.actorId;
const _errs5 = errors;
const _errs6 = errors;
if(errors === _errs6){
if(typeof data1 === "string"){
if(!pattern6.test(data1)){
validate107.errors = [{instancePath:instancePath+"/actorId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate107.errors = [{instancePath:instancePath+"/actorId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.references !== undefined){
const _errs8 = errors;
if(!(validate83(data.references, {instancePath:instancePath+"/references",parentData:data,parentDataProperty:"references",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate83.errors : vErrors.concat(validate83.errors);
errors = vErrors.length;
}
var valid0 = _errs8 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.rationale !== undefined){
let data3 = data.rationale;
const _errs9 = errors;
const _errs10 = errors;
if(errors === _errs10){
if(typeof data3 === "string"){
if(func1(data3) < 1){
validate107.errors = [{instancePath:instancePath+"/rationale",schemaPath:"#/$defs/text/minLength",keyword:"minLength",params:{limit: 1},message:"must NOT have fewer than 1 characters"}];
return false;
}
}
else {
validate107.errors = [{instancePath:instancePath+"/rationale",schemaPath:"#/$defs/text/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs9 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.links !== undefined){
let data4 = data.links;
const _errs12 = errors;
if(errors === _errs12){
if(Array.isArray(data4)){
var valid4 = true;
const len0 = data4.length;
for(let i0=0; i0<len0; i0++){
const _errs14 = errors;
if(!(validate109(data4[i0], {instancePath:instancePath+"/links/" + i0,parentData:data4,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate109.errors : vErrors.concat(validate109.errors);
errors = vErrors.length;
}
var valid4 = _errs14 === errors;
if(!valid4){
break;
}
}
}
else {
validate107.errors = [{instancePath:instancePath+"/links",schemaPath:"#/properties/links/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs12 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.blocked !== undefined){
const _errs15 = errors;
if(!(validate83(data.blocked, {instancePath:instancePath+"/blocked",parentData:data,parentDataProperty:"blocked",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate83.errors : vErrors.concat(validate83.errors);
errors = vErrors.length;
}
var valid0 = _errs15 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
}
}
else {
validate107.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate107.errors = vErrors;
return errors === 0;
}
validate107.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema130 = {"type":"object","additionalProperties":false,"required":["consumerId","projectId","createdBy","clauses","contexts","dependencies","assessmentId","provenance"],"properties":{"consumerId":{"$ref":"#/$defs/uuid"},"projectId":{"$ref":"#/$defs/uuid"},"createdBy":{"$ref":"#/$defs/uuid"},"clauses":{"allOf":[{"$ref":"#/$defs/clauses"},{"minItems":1}]},"contexts":{"$ref":"#/$defs/contexts"},"dependencies":{"$ref":"#/$defs/references"},"assessmentId":{"$ref":"#/$defs/uuid"},"provenance":{"type":"array","items":{"$ref":"#/$defs/provenanceEntry"}}}};
const schema134 = {"type":"array","uniqueItems":true,"items":{"$ref":"#/$defs/clauseReference"}};

function validate116(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate116.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(Array.isArray(data)){
var valid0 = true;
const len0 = data.length;
for(let i0=0; i0<len0; i0++){
const _errs1 = errors;
if(!(validate47(data[i0], {instancePath:instancePath+"/" + i0,parentData:data,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate47.errors : vErrors.concat(validate47.errors);
errors = vErrors.length;
}
var valid0 = _errs1 === errors;
if(!valid0){
break;
}
}
if(valid0){
let i1 = data.length;
let j0;
if(i1 > 1){
outer0:
for(;i1--;){
for(j0 = i1; j0--;){
if(func0(data[i1], data[j0])){
validate116.errors = [{instancePath,schemaPath:"#/uniqueItems",keyword:"uniqueItems",params:{i: i1, j: j0},message:"must NOT have duplicate items (items ## "+j0+" and "+i1+" are identical)"}];
return false;
break outer0;
}
}
}
}
}
}
else {
validate116.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
validate116.errors = vErrors;
return errors === 0;
}
validate116.evaluated = {"items":true,"dynamicProps":false,"dynamicItems":false};

const schema136 = {"type":"object","additionalProperties":false,"required":["reference","trace"],"properties":{"reference":{"$ref":"#/$defs/reference"},"trace":{"$ref":"#/$defs/trace"}}};

function validate121(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate121.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((data.reference === undefined) && (missing0 = "reference")) || ((data.trace === undefined) && (missing0 = "trace"))){
validate121.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((key0 === "reference") || (key0 === "trace"))){
validate121.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.reference !== undefined){
const _errs2 = errors;
if(!(validate46(data.reference, {instancePath:instancePath+"/reference",parentData:data,parentDataProperty:"reference",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.trace !== undefined){
const _errs3 = errors;
if(!(validate55(data.trace, {instancePath:instancePath+"/trace",parentData:data,parentDataProperty:"trace",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate55.errors : vErrors.concat(validate55.errors);
errors = vErrors.length;
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
else {
validate121.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate121.errors = vErrors;
return errors === 0;
}
validate121.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate115(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate115.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((((((data.consumerId === undefined) && (missing0 = "consumerId")) || ((data.projectId === undefined) && (missing0 = "projectId"))) || ((data.createdBy === undefined) && (missing0 = "createdBy"))) || ((data.clauses === undefined) && (missing0 = "clauses"))) || ((data.contexts === undefined) && (missing0 = "contexts"))) || ((data.dependencies === undefined) && (missing0 = "dependencies"))) || ((data.assessmentId === undefined) && (missing0 = "assessmentId"))) || ((data.provenance === undefined) && (missing0 = "provenance"))){
validate115.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((((((key0 === "consumerId") || (key0 === "projectId")) || (key0 === "createdBy")) || (key0 === "clauses")) || (key0 === "contexts")) || (key0 === "dependencies")) || (key0 === "assessmentId")) || (key0 === "provenance"))){
validate115.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.consumerId !== undefined){
let data0 = data.consumerId;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate115.errors = [{instancePath:instancePath+"/consumerId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate115.errors = [{instancePath:instancePath+"/consumerId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.projectId !== undefined){
let data1 = data.projectId;
const _errs5 = errors;
const _errs6 = errors;
if(errors === _errs6){
if(typeof data1 === "string"){
if(!pattern6.test(data1)){
validate115.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate115.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.createdBy !== undefined){
let data2 = data.createdBy;
const _errs8 = errors;
const _errs9 = errors;
if(errors === _errs9){
if(typeof data2 === "string"){
if(!pattern6.test(data2)){
validate115.errors = [{instancePath:instancePath+"/createdBy",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate115.errors = [{instancePath:instancePath+"/createdBy",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs8 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.clauses !== undefined){
let data3 = data.clauses;
const _errs11 = errors;
const _errs12 = errors;
if(!(validate116(data3, {instancePath:instancePath+"/clauses",parentData:data,parentDataProperty:"clauses",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate116.errors : vErrors.concat(validate116.errors);
errors = vErrors.length;
}
var valid4 = _errs12 === errors;
if(valid4){
const _errs13 = errors;
if(Array.isArray(data3)){
if(data3.length < 1){
validate115.errors = [{instancePath:instancePath+"/clauses",schemaPath:"#/properties/clauses/allOf/1/minItems",keyword:"minItems",params:{limit: 1},message:"must NOT have fewer than 1 items"}];
return false;
}
}
var valid4 = _errs13 === errors;
}
var valid0 = _errs11 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.contexts !== undefined){
const _errs14 = errors;
if(!(validate80(data.contexts, {instancePath:instancePath+"/contexts",parentData:data,parentDataProperty:"contexts",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate80.errors : vErrors.concat(validate80.errors);
errors = vErrors.length;
}
var valid0 = _errs14 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.dependencies !== undefined){
const _errs15 = errors;
if(!(validate83(data.dependencies, {instancePath:instancePath+"/dependencies",parentData:data,parentDataProperty:"dependencies",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate83.errors : vErrors.concat(validate83.errors);
errors = vErrors.length;
}
var valid0 = _errs15 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.assessmentId !== undefined){
let data6 = data.assessmentId;
const _errs16 = errors;
const _errs17 = errors;
if(errors === _errs17){
if(typeof data6 === "string"){
if(!pattern6.test(data6)){
validate115.errors = [{instancePath:instancePath+"/assessmentId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate115.errors = [{instancePath:instancePath+"/assessmentId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs16 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.provenance !== undefined){
let data7 = data.provenance;
const _errs19 = errors;
if(errors === _errs19){
if(Array.isArray(data7)){
var valid6 = true;
const len0 = data7.length;
for(let i0=0; i0<len0; i0++){
const _errs21 = errors;
if(!(validate121(data7[i0], {instancePath:instancePath+"/provenance/" + i0,parentData:data7,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate121.errors : vErrors.concat(validate121.errors);
errors = vErrors.length;
}
var valid6 = _errs21 === errors;
if(!valid6){
break;
}
}
}
else {
validate115.errors = [{instancePath:instancePath+"/provenance",schemaPath:"#/properties/provenance/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs19 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
}
}
}
}
else {
validate115.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate115.errors = vErrors;
return errors === 0;
}
validate115.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema137 = {"type":"object","additionalProperties":false,"required":["reference"],"properties":{"reference":{"$ref":"#/$defs/reference"}}};

function validate127(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate127.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if((data.reference === undefined) && (missing0 = "reference")){
validate127.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!(key0 === "reference")){
validate127.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.reference !== undefined){
if(!(validate46(data.reference, {instancePath:instancePath+"/reference",parentData:data,parentDataProperty:"reference",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
}
}
}
}
else {
validate127.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate127.errors = vErrors;
return errors === 0;
}
validate127.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema138 = {"type":"object","additionalProperties":false,"required":["generation"],"properties":{"generation":{"$ref":"#/$defs/generation"}}};

function validate131(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate131.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if((data.generation === undefined) && (missing0 = "generation")){
validate131.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!(key0 === "generation")){
validate131.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.generation !== undefined){
let data0 = data.generation;
const _errs3 = errors;
if(!((typeof data0 == "number") && (!(data0 % 1) && !isNaN(data0)))){
validate131.errors = [{instancePath:instancePath+"/generation",schemaPath:"#/$defs/generation/type",keyword:"type",params:{type: "integer"},message:"must be integer"}];
return false;
}
if(errors === _errs3){
if(typeof data0 == "number"){
if(data0 > 9223372036854776000 || isNaN(data0)){
validate131.errors = [{instancePath:instancePath+"/generation",schemaPath:"#/$defs/generation/maximum",keyword:"maximum",params:{comparison: "<=", limit: 9223372036854776000},message:"must be <= 9223372036854776000"}];
return false;
}
else {
if(data0 < 0 || isNaN(data0)){
validate131.errors = [{instancePath:instancePath+"/generation",schemaPath:"#/$defs/generation/minimum",keyword:"minimum",params:{comparison: ">=", limit: 0},message:"must be >= 0"}];
return false;
}
}
}
}
}
}
}
}
else {
validate131.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate131.errors = vErrors;
return errors === 0;
}
validate131.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate60(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate60.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
const _errs1 = errors;
let valid0 = false;
let passing0 = null;
const _errs2 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs3 = errors;
if("revisions" !== data.family){
const err0 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/0/properties/family/const",keyword:"const",params:{allowedValue: "revisions"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
var valid1 = _errs3 === errors;
}
else {
var valid1 = true;
}
if(valid1){
if(data.key !== undefined){
const _errs4 = errors;
if(!(validate26(data.key, {instancePath:instancePath+"/key",parentData:data,parentDataProperty:"key",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate26.errors : vErrors.concat(validate26.errors);
errors = vErrors.length;
}
var valid1 = _errs4 === errors;
}
else {
var valid1 = true;
}
if(valid1){
if(data.payload !== undefined){
const _errs5 = errors;
if(!(validate62(data.payload, {instancePath:instancePath+"/payload",parentData:data,parentDataProperty:"payload",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate62.errors : vErrors.concat(validate62.errors);
errors = vErrors.length;
}
var valid1 = _errs5 === errors;
}
else {
var valid1 = true;
}
}
}
}
var _valid0 = _errs2 === errors;
if(_valid0){
valid0 = true;
passing0 = 0;
var props0 = {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
const _errs6 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs7 = errors;
if("proposals" !== data.family){
const err1 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/1/properties/family/const",keyword:"const",params:{allowedValue: "proposals"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
}
var valid2 = _errs7 === errors;
}
else {
var valid2 = true;
}
if(valid2){
if(data.key !== undefined){
const _errs8 = errors;
if(!(validate26(data.key, {instancePath:instancePath+"/key",parentData:data,parentDataProperty:"key",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate26.errors : vErrors.concat(validate26.errors);
errors = vErrors.length;
}
var valid2 = _errs8 === errors;
}
else {
var valid2 = true;
}
if(valid2){
if(data.payload !== undefined){
const _errs9 = errors;
if(!(validate66(data.payload, {instancePath:instancePath+"/payload",parentData:data,parentDataProperty:"payload",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate66.errors : vErrors.concat(validate66.errors);
errors = vErrors.length;
}
var valid2 = _errs9 === errors;
}
else {
var valid2 = true;
}
}
}
}
var _valid0 = _errs6 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid0 = true;
passing0 = 1;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
const _errs10 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs11 = errors;
if("grants" !== data.family){
const err2 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/2/properties/family/const",keyword:"const",params:{allowedValue: "grants"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err2];
}
else {
vErrors.push(err2);
}
errors++;
}
var valid3 = _errs11 === errors;
}
else {
var valid3 = true;
}
if(valid3){
if(data.key !== undefined){
const _errs12 = errors;
if(!(validate68(data.key, {instancePath:instancePath+"/key",parentData:data,parentDataProperty:"key",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate68.errors : vErrors.concat(validate68.errors);
errors = vErrors.length;
}
var valid3 = _errs12 === errors;
}
else {
var valid3 = true;
}
if(valid3){
if(data.payload !== undefined){
let data8 = data.payload;
const _errs13 = errors;
const _errs14 = errors;
if(errors === _errs14){
if(data8 && typeof data8 == "object" && !Array.isArray(data8)){
let missing0;
if((data8.capabilities === undefined) && (missing0 = "capabilities")){
const err3 = {instancePath:instancePath+"/payload",schemaPath:"#/$defs/grantPayload/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"};
if(vErrors === null){
vErrors = [err3];
}
else {
vErrors.push(err3);
}
errors++;
}
else {
const _errs16 = errors;
for(const key0 in data8){
if(!(key0 === "capabilities")){
const err4 = {instancePath:instancePath+"/payload",schemaPath:"#/$defs/grantPayload/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err4];
}
else {
vErrors.push(err4);
}
errors++;
break;
}
}
if(_errs16 === errors){
if(data8.capabilities !== undefined){
let data9 = data8.capabilities;
const _errs17 = errors;
if(errors === _errs17){
if(Array.isArray(data9)){
var valid6 = true;
const len0 = data9.length;
for(let i0=0; i0<len0; i0++){
let data10 = data9[i0];
const _errs19 = errors;
if(!(((((data10 === "READ") || (data10 === "PROPOSE")) || (data10 === "PLAN")) || (data10 === "APPROVE")) || (data10 === "MANAGE"))){
const err5 = {instancePath:instancePath+"/payload/capabilities/" + i0,schemaPath:"#/$defs/grantPayload/properties/capabilities/items/enum",keyword:"enum",params:{allowedValues: schema93.properties.capabilities.items.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err5];
}
else {
vErrors.push(err5);
}
errors++;
}
var valid6 = _errs19 === errors;
if(!valid6){
break;
}
}
if(valid6){
let i1 = data9.length;
let j0;
if(i1 > 1){
outer0:
for(;i1--;){
for(j0 = i1; j0--;){
if(func0(data9[i1], data9[j0])){
const err6 = {instancePath:instancePath+"/payload/capabilities",schemaPath:"#/$defs/grantPayload/properties/capabilities/uniqueItems",keyword:"uniqueItems",params:{i: i1, j: j0},message:"must NOT have duplicate items (items ## "+j0+" and "+i1+" are identical)"};
if(vErrors === null){
vErrors = [err6];
}
else {
vErrors.push(err6);
}
errors++;
break outer0;
}
}
}
}
}
}
else {
const err7 = {instancePath:instancePath+"/payload/capabilities",schemaPath:"#/$defs/grantPayload/properties/capabilities/type",keyword:"type",params:{type: "array"},message:"must be array"};
if(vErrors === null){
vErrors = [err7];
}
else {
vErrors.push(err7);
}
errors++;
}
}
}
}
}
}
else {
const err8 = {instancePath:instancePath+"/payload",schemaPath:"#/$defs/grantPayload/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err8];
}
else {
vErrors.push(err8);
}
errors++;
}
}
var valid3 = _errs13 === errors;
}
else {
var valid3 = true;
}
}
}
}
var _valid0 = _errs10 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 2];
}
else {
if(_valid0){
valid0 = true;
passing0 = 2;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
const _errs20 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs21 = errors;
if("requests" !== data.family){
const err9 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/3/properties/family/const",keyword:"const",params:{allowedValue: "requests"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err9];
}
else {
vErrors.push(err9);
}
errors++;
}
var valid8 = _errs21 === errors;
}
else {
var valid8 = true;
}
if(valid8){
if(data.key !== undefined){
let data12 = data.key;
const _errs22 = errors;
const _errs23 = errors;
if(errors === _errs23){
if(typeof data12 === "string"){
if(!pattern6.test(data12)){
const err10 = {instancePath:instancePath+"/key",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err10];
}
else {
vErrors.push(err10);
}
errors++;
}
}
else {
const err11 = {instancePath:instancePath+"/key",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err11];
}
else {
vErrors.push(err11);
}
errors++;
}
}
var valid8 = _errs22 === errors;
}
else {
var valid8 = true;
}
if(valid8){
if(data.payload !== undefined){
const _errs25 = errors;
if(!(validate70(data.payload, {instancePath:instancePath+"/payload",parentData:data,parentDataProperty:"payload",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate70.errors : vErrors.concat(validate70.errors);
errors = vErrors.length;
}
var valid8 = _errs25 === errors;
}
else {
var valid8 = true;
}
}
}
}
var _valid0 = _errs20 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 3];
}
else {
if(_valid0){
valid0 = true;
passing0 = 3;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
const _errs26 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs27 = errors;
if("resolutions" !== data.family){
const err12 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/4/properties/family/const",keyword:"const",params:{allowedValue: "resolutions"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err12];
}
else {
vErrors.push(err12);
}
errors++;
}
var valid10 = _errs27 === errors;
}
else {
var valid10 = true;
}
if(valid10){
if(data.key !== undefined){
let data15 = data.key;
const _errs28 = errors;
const _errs29 = errors;
if(errors === _errs29){
if(typeof data15 === "string"){
if(!pattern6.test(data15)){
const err13 = {instancePath:instancePath+"/key",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err13];
}
else {
vErrors.push(err13);
}
errors++;
}
}
else {
const err14 = {instancePath:instancePath+"/key",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err14];
}
else {
vErrors.push(err14);
}
errors++;
}
}
var valid10 = _errs28 === errors;
}
else {
var valid10 = true;
}
if(valid10){
if(data.payload !== undefined){
const _errs31 = errors;
if(!(validate94(data.payload, {instancePath:instancePath+"/payload",parentData:data,parentDataProperty:"payload",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate94.errors : vErrors.concat(validate94.errors);
errors = vErrors.length;
}
var valid10 = _errs31 === errors;
}
else {
var valid10 = true;
}
}
}
}
var _valid0 = _errs26 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 4];
}
else {
if(_valid0){
valid0 = true;
passing0 = 4;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
const _errs32 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs33 = errors;
if("approvals" !== data.family){
const err15 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/5/properties/family/const",keyword:"const",params:{allowedValue: "approvals"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err15];
}
else {
vErrors.push(err15);
}
errors++;
}
var valid12 = _errs33 === errors;
}
else {
var valid12 = true;
}
if(valid12){
if(data.key !== undefined){
const _errs34 = errors;
if(!(validate46(data.key, {instancePath:instancePath+"/key",parentData:data,parentDataProperty:"key",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid12 = _errs34 === errors;
}
else {
var valid12 = true;
}
if(valid12){
if(data.payload !== undefined){
const _errs35 = errors;
if(!(validate55(data.payload, {instancePath:instancePath+"/payload",parentData:data,parentDataProperty:"payload",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate55.errors : vErrors.concat(validate55.errors);
errors = vErrors.length;
}
var valid12 = _errs35 === errors;
}
else {
var valid12 = true;
}
}
}
}
var _valid0 = _errs32 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 5];
}
else {
if(_valid0){
valid0 = true;
passing0 = 5;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
const _errs36 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs37 = errors;
if("continuity" !== data.family){
const err16 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/6/properties/family/const",keyword:"const",params:{allowedValue: "continuity"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err16];
}
else {
vErrors.push(err16);
}
errors++;
}
var valid13 = _errs37 === errors;
}
else {
var valid13 = true;
}
if(valid13){
if(data.key !== undefined){
const _errs38 = errors;
if(!(validate46(data.key, {instancePath:instancePath+"/key",parentData:data,parentDataProperty:"key",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid13 = _errs38 === errors;
}
else {
var valid13 = true;
}
if(valid13){
if(data.payload !== undefined){
const _errs39 = errors;
if(!(validate104(data.payload, {instancePath:instancePath+"/payload",parentData:data,parentDataProperty:"payload",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate104.errors : vErrors.concat(validate104.errors);
errors = vErrors.length;
}
var valid13 = _errs39 === errors;
}
else {
var valid13 = true;
}
}
}
}
var _valid0 = _errs36 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 6];
}
else {
if(_valid0){
valid0 = true;
passing0 = 6;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
const _errs40 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs41 = errors;
if("assessments" !== data.family){
const err17 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/7/properties/family/const",keyword:"const",params:{allowedValue: "assessments"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err17];
}
else {
vErrors.push(err17);
}
errors++;
}
var valid14 = _errs41 === errors;
}
else {
var valid14 = true;
}
if(valid14){
if(data.key !== undefined){
let data24 = data.key;
const _errs42 = errors;
const _errs43 = errors;
if(errors === _errs43){
if(typeof data24 === "string"){
if(!pattern6.test(data24)){
const err18 = {instancePath:instancePath+"/key",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err18];
}
else {
vErrors.push(err18);
}
errors++;
}
}
else {
const err19 = {instancePath:instancePath+"/key",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err19];
}
else {
vErrors.push(err19);
}
errors++;
}
}
var valid14 = _errs42 === errors;
}
else {
var valid14 = true;
}
if(valid14){
if(data.payload !== undefined){
const _errs45 = errors;
if(!(validate107(data.payload, {instancePath:instancePath+"/payload",parentData:data,parentDataProperty:"payload",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate107.errors : vErrors.concat(validate107.errors);
errors = vErrors.length;
}
var valid14 = _errs45 === errors;
}
else {
var valid14 = true;
}
}
}
}
var _valid0 = _errs40 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 7];
}
else {
if(_valid0){
valid0 = true;
passing0 = 7;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
const _errs46 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs47 = errors;
if("manifests" !== data.family){
const err20 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/8/properties/family/const",keyword:"const",params:{allowedValue: "manifests"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err20];
}
else {
vErrors.push(err20);
}
errors++;
}
var valid16 = _errs47 === errors;
}
else {
var valid16 = true;
}
if(valid16){
if(data.key !== undefined){
let data27 = data.key;
const _errs48 = errors;
const _errs49 = errors;
if(errors === _errs49){
if(typeof data27 === "string"){
if(!pattern6.test(data27)){
const err21 = {instancePath:instancePath+"/key",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err21];
}
else {
vErrors.push(err21);
}
errors++;
}
}
else {
const err22 = {instancePath:instancePath+"/key",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err22];
}
else {
vErrors.push(err22);
}
errors++;
}
}
var valid16 = _errs48 === errors;
}
else {
var valid16 = true;
}
if(valid16){
if(data.payload !== undefined){
const _errs51 = errors;
if(!(validate115(data.payload, {instancePath:instancePath+"/payload",parentData:data,parentDataProperty:"payload",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate115.errors : vErrors.concat(validate115.errors);
errors = vErrors.length;
}
var valid16 = _errs51 === errors;
}
else {
var valid16 = true;
}
}
}
}
var _valid0 = _errs46 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 8];
}
else {
if(_valid0){
valid0 = true;
passing0 = 8;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
const _errs52 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs53 = errors;
if("heads" !== data.family){
const err23 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/9/properties/family/const",keyword:"const",params:{allowedValue: "heads"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err23];
}
else {
vErrors.push(err23);
}
errors++;
}
var valid18 = _errs53 === errors;
}
else {
var valid18 = true;
}
if(valid18){
if(data.key !== undefined){
const _errs54 = errors;
if(!(validate89(data.key, {instancePath:instancePath+"/key",parentData:data,parentDataProperty:"key",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate89.errors : vErrors.concat(validate89.errors);
errors = vErrors.length;
}
var valid18 = _errs54 === errors;
}
else {
var valid18 = true;
}
if(valid18){
if(data.payload !== undefined){
const _errs55 = errors;
if(!(validate127(data.payload, {instancePath:instancePath+"/payload",parentData:data,parentDataProperty:"payload",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate127.errors : vErrors.concat(validate127.errors);
errors = vErrors.length;
}
var valid18 = _errs55 === errors;
}
else {
var valid18 = true;
}
}
}
}
var _valid0 = _errs52 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 9];
}
else {
if(_valid0){
valid0 = true;
passing0 = 9;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
const _errs56 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs57 = errors;
if("generations" !== data.family){
const err24 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/10/properties/family/const",keyword:"const",params:{allowedValue: "generations"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err24];
}
else {
vErrors.push(err24);
}
errors++;
}
var valid19 = _errs57 === errors;
}
else {
var valid19 = true;
}
if(valid19){
if(data.key !== undefined){
const _errs58 = errors;
if(!(validate89(data.key, {instancePath:instancePath+"/key",parentData:data,parentDataProperty:"key",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate89.errors : vErrors.concat(validate89.errors);
errors = vErrors.length;
}
var valid19 = _errs58 === errors;
}
else {
var valid19 = true;
}
if(valid19){
if(data.payload !== undefined){
const _errs59 = errors;
if(!(validate131(data.payload, {instancePath:instancePath+"/payload",parentData:data,parentDataProperty:"payload",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate131.errors : vErrors.concat(validate131.errors);
errors = vErrors.length;
}
var valid19 = _errs59 === errors;
}
else {
var valid19 = true;
}
}
}
}
var _valid0 = _errs56 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 10];
}
else {
if(_valid0){
valid0 = true;
passing0 = 10;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
const _errs60 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.family !== undefined){
const _errs61 = errors;
if("blocked" !== data.family){
const err25 = {instancePath:instancePath+"/family",schemaPath:"#/oneOf/11/properties/family/const",keyword:"const",params:{allowedValue: "blocked"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err25];
}
else {
vErrors.push(err25);
}
errors++;
}
var valid20 = _errs61 === errors;
}
else {
var valid20 = true;
}
if(valid20){
if(data.key !== undefined){
const _errs62 = errors;
if(!(validate46(data.key, {instancePath:instancePath+"/key",parentData:data,parentDataProperty:"key",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate46.errors : vErrors.concat(validate46.errors);
errors = vErrors.length;
}
var valid20 = _errs62 === errors;
}
else {
var valid20 = true;
}
if(valid20){
if(data.payload !== undefined){
let data37 = data.payload;
const _errs63 = errors;
const _errs64 = errors;
if(errors === _errs64){
if(data37 && typeof data37 == "object" && !Array.isArray(data37)){
if(Object.keys(data37).length > 0){
const err26 = {instancePath:instancePath+"/payload",schemaPath:"#/$defs/emptyPayload/maxProperties",keyword:"maxProperties",params:{limit: 0},message:"must NOT have more than 0 properties"};
if(vErrors === null){
vErrors = [err26];
}
else {
vErrors.push(err26);
}
errors++;
}
else {
for(const key1 in data37){
const err27 = {instancePath:instancePath+"/payload",schemaPath:"#/$defs/emptyPayload/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key1},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err27];
}
else {
vErrors.push(err27);
}
errors++;
break;
}
}
}
else {
const err28 = {instancePath:instancePath+"/payload",schemaPath:"#/$defs/emptyPayload/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err28];
}
else {
vErrors.push(err28);
}
errors++;
}
}
var valid20 = _errs63 === errors;
}
else {
var valid20 = true;
}
}
}
}
var _valid0 = _errs60 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 11];
}
else {
if(_valid0){
valid0 = true;
passing0 = 11;
if(props0 !== true){
props0 = props0 || {};
props0.family = true;
props0.key = true;
props0.payload = true;
}
}
}
}
}
}
}
}
}
}
}
}
}
if(!valid0){
const err29 = {instancePath,schemaPath:"#/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err29];
}
else {
vErrors.push(err29);
}
errors++;
validate60.errors = vErrors;
return false;
}
else {
errors = _errs1;
if(vErrors !== null){
if(_errs1){
vErrors.length = _errs1;
}
else {
vErrors = null;
}
}
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing1;
if(((((data.formatVersion === undefined) && (missing1 = "formatVersion")) || ((data.family === undefined) && (missing1 = "family"))) || ((data.key === undefined) && (missing1 = "key"))) || ((data.payload === undefined) && (missing1 = "payload"))){
validate60.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing1},message:"must have required property '"+missing1+"'"}];
return false;
}
else {
const _errs67 = errors;
for(const key2 in data){
if(!((((key2 === "formatVersion") || (key2 === "family")) || (key2 === "key")) || (key2 === "payload"))){
validate60.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key2},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs67 === errors){
if(data.formatVersion !== undefined){
const _errs68 = errors;
if(1 !== data.formatVersion){
validate60.errors = [{instancePath:instancePath+"/formatVersion",schemaPath:"#/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"}];
return false;
}
var valid22 = _errs68 === errors;
}
else {
var valid22 = true;
}
if(valid22){
if(data.family !== undefined){
const _errs69 = errors;
if(typeof data.family !== "string"){
validate60.errors = [{instancePath:instancePath+"/family",schemaPath:"#/properties/family/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
var valid22 = _errs69 === errors;
}
else {
var valid22 = true;
}
}
}
}
}
else {
validate60.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate60.errors = vErrors;
return errors === 0;
}
validate60.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema145 = {"type":"object","additionalProperties":false,"properties":{"request":{"$ref":"#/$defs/record"},"resolution":{"oneOf":[{"$ref":"#/$defs/record"},{"type":"null"}]},"materials":{"type":"array","items":{"$ref":"#/$defs/material"}},"actionable":{"type":"boolean"},"blockers":{"type":"array","items":{"type":"string"}},"complete":{"const":true}},"required":["request","resolution","materials","actionable","blockers","complete"]};

function validate138(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate138.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((((data.request === undefined) && (missing0 = "request")) || ((data.resolution === undefined) && (missing0 = "resolution"))) || ((data.materials === undefined) && (missing0 = "materials"))) || ((data.actionable === undefined) && (missing0 = "actionable"))) || ((data.blockers === undefined) && (missing0 = "blockers"))) || ((data.complete === undefined) && (missing0 = "complete"))){
validate138.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((((key0 === "request") || (key0 === "resolution")) || (key0 === "materials")) || (key0 === "actionable")) || (key0 === "blockers")) || (key0 === "complete"))){
validate138.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.request !== undefined){
const _errs2 = errors;
if(!(validate60(data.request, {instancePath:instancePath+"/request",parentData:data,parentDataProperty:"request",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate60.errors : vErrors.concat(validate60.errors);
errors = vErrors.length;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.resolution !== undefined){
let data1 = data.resolution;
const _errs3 = errors;
const _errs4 = errors;
let valid1 = false;
let passing0 = null;
const _errs5 = errors;
if(!(validate60(data1, {instancePath:instancePath+"/resolution",parentData:data,parentDataProperty:"resolution",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate60.errors : vErrors.concat(validate60.errors);
errors = vErrors.length;
}
var _valid0 = _errs5 === errors;
if(_valid0){
valid1 = true;
passing0 = 0;
}
const _errs6 = errors;
if(data1 !== null){
const err0 = {instancePath:instancePath+"/resolution",schemaPath:"#/properties/resolution/oneOf/1/type",keyword:"type",params:{type: "null"},message:"must be null"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
var _valid0 = _errs6 === errors;
if(_valid0 && valid1){
valid1 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid1 = true;
passing0 = 1;
}
}
if(!valid1){
const err1 = {instancePath:instancePath+"/resolution",schemaPath:"#/properties/resolution/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
validate138.errors = vErrors;
return false;
}
else {
errors = _errs4;
if(vErrors !== null){
if(_errs4){
vErrors.length = _errs4;
}
else {
vErrors = null;
}
}
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.materials !== undefined){
let data2 = data.materials;
const _errs8 = errors;
if(errors === _errs8){
if(Array.isArray(data2)){
var valid2 = true;
const len0 = data2.length;
for(let i0=0; i0<len0; i0++){
const _errs10 = errors;
if(!(validate45(data2[i0], {instancePath:instancePath+"/materials/" + i0,parentData:data2,parentDataProperty:i0,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate45.errors : vErrors.concat(validate45.errors);
errors = vErrors.length;
}
var valid2 = _errs10 === errors;
if(!valid2){
break;
}
}
}
else {
validate138.errors = [{instancePath:instancePath+"/materials",schemaPath:"#/properties/materials/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs8 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.actionable !== undefined){
const _errs11 = errors;
if(typeof data.actionable !== "boolean"){
validate138.errors = [{instancePath:instancePath+"/actionable",schemaPath:"#/properties/actionable/type",keyword:"type",params:{type: "boolean"},message:"must be boolean"}];
return false;
}
var valid0 = _errs11 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.blockers !== undefined){
let data5 = data.blockers;
const _errs13 = errors;
if(errors === _errs13){
if(Array.isArray(data5)){
var valid3 = true;
const len1 = data5.length;
for(let i1=0; i1<len1; i1++){
const _errs15 = errors;
if(typeof data5[i1] !== "string"){
validate138.errors = [{instancePath:instancePath+"/blockers/" + i1,schemaPath:"#/properties/blockers/items/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
var valid3 = _errs15 === errors;
if(!valid3){
break;
}
}
}
else {
validate138.errors = [{instancePath:instancePath+"/blockers",schemaPath:"#/properties/blockers/type",keyword:"type",params:{type: "array"},message:"must be array"}];
return false;
}
}
var valid0 = _errs13 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.complete !== undefined){
const _errs17 = errors;
if(true !== data.complete){
validate138.errors = [{instancePath:instancePath+"/complete",schemaPath:"#/properties/complete/const",keyword:"const",params:{allowedValue: true},message:"must be equal to constant"}];
return false;
}
var valid0 = _errs17 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
}
}
else {
validate138.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate138.errors = vErrors;
return errors === 0;
}
validate138.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema146 = {"type":"object","additionalProperties":false,"required":["principalId","projectId","operation","requestKey"],"properties":{"principalId":{"$ref":"#/$defs/uuid"},"projectId":{"$ref":"#/$defs/uuid"},"operation":{"$ref":"#/$defs/operation"},"requestKey":{"$ref":"#/$defs/uuid"}}};
const schema149 = {"oneOf":[{"$ref":"#/$defs/coreOperation"},{"$ref":"#/$defs/authOperation"}]};
const schema150 = {"enum":["propose_revision","request_approval","resolve_approval","record_assessment","pin_consumer","revoke_access"]};
const schema151 = {"enum":["replace_grants","revoke_credential","rotate_credential"]};

function validate144(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate144.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
const _errs0 = errors;
let valid0 = false;
let passing0 = null;
const _errs1 = errors;
if(!((((((data === "propose_revision") || (data === "request_approval")) || (data === "resolve_approval")) || (data === "record_assessment")) || (data === "pin_consumer")) || (data === "revoke_access"))){
const err0 = {instancePath,schemaPath:"#/$defs/coreOperation/enum",keyword:"enum",params:{allowedValues: schema150.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
var _valid0 = _errs1 === errors;
if(_valid0){
valid0 = true;
passing0 = 0;
}
const _errs3 = errors;
if(!(((data === "replace_grants") || (data === "revoke_credential")) || (data === "rotate_credential"))){
const err1 = {instancePath,schemaPath:"#/$defs/authOperation/enum",keyword:"enum",params:{allowedValues: schema151.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
}
var _valid0 = _errs3 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid0 = true;
passing0 = 1;
}
}
if(!valid0){
const err2 = {instancePath,schemaPath:"#/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err2];
}
else {
vErrors.push(err2);
}
errors++;
validate144.errors = vErrors;
return false;
}
else {
errors = _errs0;
if(vErrors !== null){
if(_errs0){
vErrors.length = _errs0;
}
else {
vErrors = null;
}
}
}
validate144.errors = vErrors;
return errors === 0;
}
validate144.evaluated = {"dynamicProps":false,"dynamicItems":false};


function validate143(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate143.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((data.principalId === undefined) && (missing0 = "principalId")) || ((data.projectId === undefined) && (missing0 = "projectId"))) || ((data.operation === undefined) && (missing0 = "operation"))) || ((data.requestKey === undefined) && (missing0 = "requestKey"))){
validate143.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((key0 === "principalId") || (key0 === "projectId")) || (key0 === "operation")) || (key0 === "requestKey"))){
validate143.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.principalId !== undefined){
let data0 = data.principalId;
const _errs2 = errors;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data0 === "string"){
if(!pattern6.test(data0)){
validate143.errors = [{instancePath:instancePath+"/principalId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate143.errors = [{instancePath:instancePath+"/principalId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.projectId !== undefined){
let data1 = data.projectId;
const _errs5 = errors;
const _errs6 = errors;
if(errors === _errs6){
if(typeof data1 === "string"){
if(!pattern6.test(data1)){
validate143.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate143.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.operation !== undefined){
const _errs8 = errors;
if(!(validate144(data.operation, {instancePath:instancePath+"/operation",parentData:data,parentDataProperty:"operation",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate144.errors : vErrors.concat(validate144.errors);
errors = vErrors.length;
}
var valid0 = _errs8 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.requestKey !== undefined){
let data3 = data.requestKey;
const _errs9 = errors;
const _errs10 = errors;
if(errors === _errs10){
if(typeof data3 === "string"){
if(!pattern6.test(data3)){
validate143.errors = [{instancePath:instancePath+"/requestKey",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate143.errors = [{instancePath:instancePath+"/requestKey",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs9 === errors;
}
else {
var valid0 = true;
}
}
}
}
}
}
}
else {
validate143.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate143.errors = vErrors;
return errors === 0;
}
validate143.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};

const schema156 = {"type":"object","additionalProperties":false,"required":["kind","operation","result","domainReplayed"],"properties":{"kind":{"const":"success"},"operation":{"$ref":"#/$defs/operation"},"result":{},"domainReplayed":{"type":"boolean"}},"oneOf":[{"properties":{"operation":{"const":"propose_revision"},"result":{"$ref":"#/$defs/revisionKey"}}},{"properties":{"operation":{"enum":["request_approval","resolve_approval","record_assessment","pin_consumer","revoke_access","replace_grants","revoke_credential"]},"result":{"$ref":"#/$defs/uuid"}}},{"properties":{"operation":{"const":"rotate_credential"},"result":{"type":"object","additionalProperties":false,"required":["credentialId","replacedCredentialId"],"properties":{"credentialId":{"$ref":"#/$defs/uuid"},"replacedCredentialId":{"$ref":"#/$defs/uuid"}}}}}]};

function validate147(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate147.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
const _errs1 = errors;
let valid0 = false;
let passing0 = null;
const _errs2 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.operation !== undefined){
const _errs3 = errors;
if("propose_revision" !== data.operation){
const err0 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/0/properties/operation/const",keyword:"const",params:{allowedValue: "propose_revision"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
var valid1 = _errs3 === errors;
}
else {
var valid1 = true;
}
if(valid1){
if(data.result !== undefined){
const _errs4 = errors;
if(!(validate26(data.result, {instancePath:instancePath+"/result",parentData:data,parentDataProperty:"result",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate26.errors : vErrors.concat(validate26.errors);
errors = vErrors.length;
}
var valid1 = _errs4 === errors;
}
else {
var valid1 = true;
}
}
}
var _valid0 = _errs2 === errors;
if(_valid0){
valid0 = true;
passing0 = 0;
var props0 = {};
props0.operation = true;
props0.result = true;
}
const _errs5 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.operation !== undefined){
let data2 = data.operation;
const _errs6 = errors;
if(!(((((((data2 === "request_approval") || (data2 === "resolve_approval")) || (data2 === "record_assessment")) || (data2 === "pin_consumer")) || (data2 === "revoke_access")) || (data2 === "replace_grants")) || (data2 === "revoke_credential"))){
const err1 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/1/properties/operation/enum",keyword:"enum",params:{allowedValues: schema156.oneOf[1].properties.operation.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
}
var valid2 = _errs6 === errors;
}
else {
var valid2 = true;
}
if(valid2){
if(data.result !== undefined){
let data3 = data.result;
const _errs7 = errors;
const _errs8 = errors;
if(errors === _errs8){
if(typeof data3 === "string"){
if(!pattern6.test(data3)){
const err2 = {instancePath:instancePath+"/result",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err2];
}
else {
vErrors.push(err2);
}
errors++;
}
}
else {
const err3 = {instancePath:instancePath+"/result",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err3];
}
else {
vErrors.push(err3);
}
errors++;
}
}
var valid2 = _errs7 === errors;
}
else {
var valid2 = true;
}
}
}
var _valid0 = _errs5 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid0 = true;
passing0 = 1;
if(props0 !== true){
props0 = props0 || {};
props0.operation = true;
props0.result = true;
}
}
const _errs10 = errors;
if(data && typeof data == "object" && !Array.isArray(data)){
if(data.operation !== undefined){
const _errs11 = errors;
if("rotate_credential" !== data.operation){
const err4 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/2/properties/operation/const",keyword:"const",params:{allowedValue: "rotate_credential"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err4];
}
else {
vErrors.push(err4);
}
errors++;
}
var valid4 = _errs11 === errors;
}
else {
var valid4 = true;
}
if(valid4){
if(data.result !== undefined){
let data5 = data.result;
const _errs12 = errors;
if(errors === _errs12){
if(data5 && typeof data5 == "object" && !Array.isArray(data5)){
let missing0;
if(((data5.credentialId === undefined) && (missing0 = "credentialId")) || ((data5.replacedCredentialId === undefined) && (missing0 = "replacedCredentialId"))){
const err5 = {instancePath:instancePath+"/result",schemaPath:"#/oneOf/2/properties/result/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"};
if(vErrors === null){
vErrors = [err5];
}
else {
vErrors.push(err5);
}
errors++;
}
else {
const _errs14 = errors;
for(const key0 in data5){
if(!((key0 === "credentialId") || (key0 === "replacedCredentialId"))){
const err6 = {instancePath:instancePath+"/result",schemaPath:"#/oneOf/2/properties/result/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err6];
}
else {
vErrors.push(err6);
}
errors++;
break;
}
}
if(_errs14 === errors){
if(data5.credentialId !== undefined){
let data6 = data5.credentialId;
const _errs15 = errors;
const _errs16 = errors;
if(errors === _errs16){
if(typeof data6 === "string"){
if(!pattern6.test(data6)){
const err7 = {instancePath:instancePath+"/result/credentialId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err7];
}
else {
vErrors.push(err7);
}
errors++;
}
}
else {
const err8 = {instancePath:instancePath+"/result/credentialId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err8];
}
else {
vErrors.push(err8);
}
errors++;
}
}
var valid5 = _errs15 === errors;
}
else {
var valid5 = true;
}
if(valid5){
if(data5.replacedCredentialId !== undefined){
let data7 = data5.replacedCredentialId;
const _errs18 = errors;
const _errs19 = errors;
if(errors === _errs19){
if(typeof data7 === "string"){
if(!pattern6.test(data7)){
const err9 = {instancePath:instancePath+"/result/replacedCredentialId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err9];
}
else {
vErrors.push(err9);
}
errors++;
}
}
else {
const err10 = {instancePath:instancePath+"/result/replacedCredentialId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err10];
}
else {
vErrors.push(err10);
}
errors++;
}
}
var valid5 = _errs18 === errors;
}
else {
var valid5 = true;
}
}
}
}
}
else {
const err11 = {instancePath:instancePath+"/result",schemaPath:"#/oneOf/2/properties/result/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err11];
}
else {
vErrors.push(err11);
}
errors++;
}
}
var valid4 = _errs12 === errors;
}
else {
var valid4 = true;
}
}
}
var _valid0 = _errs10 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 2];
}
else {
if(_valid0){
valid0 = true;
passing0 = 2;
if(props0 !== true){
props0 = props0 || {};
props0.operation = true;
props0.result = true;
}
}
}
}
if(!valid0){
const err12 = {instancePath,schemaPath:"#/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err12];
}
else {
vErrors.push(err12);
}
errors++;
validate147.errors = vErrors;
return false;
}
else {
errors = _errs1;
if(vErrors !== null){
if(_errs1){
vErrors.length = _errs1;
}
else {
vErrors = null;
}
}
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing1;
if(((((data.kind === undefined) && (missing1 = "kind")) || ((data.operation === undefined) && (missing1 = "operation"))) || ((data.result === undefined) && (missing1 = "result"))) || ((data.domainReplayed === undefined) && (missing1 = "domainReplayed"))){
validate147.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing1},message:"must have required property '"+missing1+"'"}];
return false;
}
else {
const _errs21 = errors;
for(const key1 in data){
if(!((((key1 === "kind") || (key1 === "operation")) || (key1 === "result")) || (key1 === "domainReplayed"))){
validate147.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key1},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs21 === errors){
if(data.kind !== undefined){
const _errs22 = errors;
if("success" !== data.kind){
validate147.errors = [{instancePath:instancePath+"/kind",schemaPath:"#/properties/kind/const",keyword:"const",params:{allowedValue: "success"},message:"must be equal to constant"}];
return false;
}
var valid8 = _errs22 === errors;
}
else {
var valid8 = true;
}
if(valid8){
if(data.operation !== undefined){
const _errs23 = errors;
if(!(validate144(data.operation, {instancePath:instancePath+"/operation",parentData:data,parentDataProperty:"operation",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate144.errors : vErrors.concat(validate144.errors);
errors = vErrors.length;
}
var valid8 = _errs23 === errors;
}
else {
var valid8 = true;
}
if(valid8){
if(data.domainReplayed !== undefined){
const _errs24 = errors;
if(typeof data.domainReplayed !== "boolean"){
validate147.errors = [{instancePath:instancePath+"/domainReplayed",schemaPath:"#/properties/domainReplayed/type",keyword:"type",params:{type: "boolean"},message:"must be boolean"}];
return false;
}
var valid8 = _errs24 === errors;
}
else {
var valid8 = true;
}
}
}
}
}
}
else {
validate147.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate147.errors = vErrors;
return errors === 0;
}
validate147.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};


function validate21(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate21.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
const _errs0 = errors;
let valid0 = false;
let passing0 = null;
const _errs1 = errors;
if(errors === _errs1){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if((((((data.formatVersion === undefined) && (missing0 = "formatVersion")) || ((data.kind === undefined) && (missing0 = "kind"))) || ((data.basis === undefined) && (missing0 = "basis"))) || ((data.operation === undefined) && (missing0 = "operation"))) || ((data.data === undefined) && (missing0 = "data"))){
const err0 = {instancePath,schemaPath:"#/oneOf/0/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
else {
const _errs3 = errors;
for(const key0 in data){
if(!(((((key0 === "formatVersion") || (key0 === "kind")) || (key0 === "basis")) || (key0 === "operation")) || (key0 === "data"))){
const err1 = {instancePath,schemaPath:"#/oneOf/0/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err1];
}
else {
vErrors.push(err1);
}
errors++;
break;
}
}
if(_errs3 === errors){
if(data.formatVersion !== undefined){
const _errs4 = errors;
if(1 !== data.formatVersion){
const err2 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/0/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err2];
}
else {
vErrors.push(err2);
}
errors++;
}
var valid1 = _errs4 === errors;
}
else {
var valid1 = true;
}
if(valid1){
if(data.kind !== undefined){
const _errs5 = errors;
if("read" !== data.kind){
const err3 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/0/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err3];
}
else {
vErrors.push(err3);
}
errors++;
}
var valid1 = _errs5 === errors;
}
else {
var valid1 = true;
}
if(valid1){
if(data.basis !== undefined){
let data2 = data.basis;
const _errs6 = errors;
const _errs7 = errors;
const _errs9 = errors;
let valid3 = false;
let passing1 = null;
const _errs10 = errors;
if(data2 && typeof data2 == "object" && !Array.isArray(data2)){
if(data2.algorithm !== undefined){
const _errs11 = errors;
if("sha1" !== data2.algorithm){
const err4 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err4];
}
else {
vErrors.push(err4);
}
errors++;
}
var valid4 = _errs11 === errors;
}
else {
var valid4 = true;
}
if(valid4){
if(data2.hex !== undefined){
let data4 = data2.hex;
const _errs12 = errors;
if(typeof data4 === "string"){
if(!pattern4.test(data4)){
const err5 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err5];
}
else {
vErrors.push(err5);
}
errors++;
}
}
var valid4 = _errs12 === errors;
}
else {
var valid4 = true;
}
}
}
var _valid1 = _errs10 === errors;
if(_valid1){
valid3 = true;
passing1 = 0;
var props0 = {};
props0.algorithm = true;
props0.hex = true;
}
const _errs13 = errors;
if(data2 && typeof data2 == "object" && !Array.isArray(data2)){
if(data2.algorithm !== undefined){
const _errs14 = errors;
if("sha256" !== data2.algorithm){
const err6 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err6];
}
else {
vErrors.push(err6);
}
errors++;
}
var valid5 = _errs14 === errors;
}
else {
var valid5 = true;
}
if(valid5){
if(data2.hex !== undefined){
let data6 = data2.hex;
const _errs15 = errors;
if(typeof data6 === "string"){
if(!pattern5.test(data6)){
const err7 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err7];
}
else {
vErrors.push(err7);
}
errors++;
}
}
var valid5 = _errs15 === errors;
}
else {
var valid5 = true;
}
}
}
var _valid1 = _errs13 === errors;
if(_valid1 && valid3){
valid3 = false;
passing1 = [passing1, 1];
}
else {
if(_valid1){
valid3 = true;
passing1 = 1;
if(props0 !== true){
props0 = props0 || {};
props0.algorithm = true;
props0.hex = true;
}
}
}
if(!valid3){
const err8 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing1},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err8];
}
else {
vErrors.push(err8);
}
errors++;
}
else {
errors = _errs9;
if(vErrors !== null){
if(_errs9){
vErrors.length = _errs9;
}
else {
vErrors = null;
}
}
}
if(errors === _errs7){
if(data2 && typeof data2 == "object" && !Array.isArray(data2)){
let missing1;
if(((data2.algorithm === undefined) && (missing1 = "algorithm")) || ((data2.hex === undefined) && (missing1 = "hex"))){
const err9 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing1},message:"must have required property '"+missing1+"'"};
if(vErrors === null){
vErrors = [err9];
}
else {
vErrors.push(err9);
}
errors++;
}
else {
const _errs16 = errors;
for(const key1 in data2){
if(!((key1 === "algorithm") || (key1 === "hex"))){
const err10 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key1},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err10];
}
else {
vErrors.push(err10);
}
errors++;
break;
}
}
if(_errs16 === errors){
if(data2.algorithm !== undefined){
let data7 = data2.algorithm;
const _errs17 = errors;
if(!((data7 === "sha1") || (data7 === "sha256"))){
const err11 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err11];
}
else {
vErrors.push(err11);
}
errors++;
}
var valid6 = _errs17 === errors;
}
else {
var valid6 = true;
}
if(valid6){
if(data2.hex !== undefined){
const _errs18 = errors;
if(typeof data2.hex !== "string"){
const err12 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err12];
}
else {
vErrors.push(err12);
}
errors++;
}
var valid6 = _errs18 === errors;
}
else {
var valid6 = true;
}
}
}
}
}
else {
const err13 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err13];
}
else {
vErrors.push(err13);
}
errors++;
}
}
var valid1 = _errs6 === errors;
}
else {
var valid1 = true;
}
if(valid1){
if(data.operation !== undefined){
const _errs20 = errors;
if("list_projects" !== data.operation){
const err14 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/0/properties/operation/const",keyword:"const",params:{allowedValue: "list_projects"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err14];
}
else {
vErrors.push(err14);
}
errors++;
}
var valid1 = _errs20 === errors;
}
else {
var valid1 = true;
}
if(valid1){
if(data.data !== undefined){
const _errs21 = errors;
if(!(validate22(data.data, {instancePath:instancePath+"/data",parentData:data,parentDataProperty:"data",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate22.errors : vErrors.concat(validate22.errors);
errors = vErrors.length;
}
var valid1 = _errs21 === errors;
}
else {
var valid1 = true;
}
}
}
}
}
}
}
}
else {
const err15 = {instancePath,schemaPath:"#/oneOf/0/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err15];
}
else {
vErrors.push(err15);
}
errors++;
}
}
var _valid0 = _errs1 === errors;
if(_valid0){
valid0 = true;
passing0 = 0;
var props1 = true;
}
const _errs22 = errors;
if(errors === _errs22){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing2;
if((((((data.formatVersion === undefined) && (missing2 = "formatVersion")) || ((data.kind === undefined) && (missing2 = "kind"))) || ((data.basis === undefined) && (missing2 = "basis"))) || ((data.operation === undefined) && (missing2 = "operation"))) || ((data.data === undefined) && (missing2 = "data"))){
const err16 = {instancePath,schemaPath:"#/oneOf/1/required",keyword:"required",params:{missingProperty: missing2},message:"must have required property '"+missing2+"'"};
if(vErrors === null){
vErrors = [err16];
}
else {
vErrors.push(err16);
}
errors++;
}
else {
const _errs24 = errors;
for(const key2 in data){
if(!(((((key2 === "formatVersion") || (key2 === "kind")) || (key2 === "basis")) || (key2 === "operation")) || (key2 === "data"))){
const err17 = {instancePath,schemaPath:"#/oneOf/1/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key2},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err17];
}
else {
vErrors.push(err17);
}
errors++;
break;
}
}
if(_errs24 === errors){
if(data.formatVersion !== undefined){
const _errs25 = errors;
if(1 !== data.formatVersion){
const err18 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/1/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err18];
}
else {
vErrors.push(err18);
}
errors++;
}
var valid7 = _errs25 === errors;
}
else {
var valid7 = true;
}
if(valid7){
if(data.kind !== undefined){
const _errs26 = errors;
if("read" !== data.kind){
const err19 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/1/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err19];
}
else {
vErrors.push(err19);
}
errors++;
}
var valid7 = _errs26 === errors;
}
else {
var valid7 = true;
}
if(valid7){
if(data.basis !== undefined){
let data13 = data.basis;
const _errs27 = errors;
const _errs28 = errors;
const _errs30 = errors;
let valid9 = false;
let passing2 = null;
const _errs31 = errors;
if(data13 && typeof data13 == "object" && !Array.isArray(data13)){
if(data13.algorithm !== undefined){
const _errs32 = errors;
if("sha1" !== data13.algorithm){
const err20 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err20];
}
else {
vErrors.push(err20);
}
errors++;
}
var valid10 = _errs32 === errors;
}
else {
var valid10 = true;
}
if(valid10){
if(data13.hex !== undefined){
let data15 = data13.hex;
const _errs33 = errors;
if(typeof data15 === "string"){
if(!pattern4.test(data15)){
const err21 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err21];
}
else {
vErrors.push(err21);
}
errors++;
}
}
var valid10 = _errs33 === errors;
}
else {
var valid10 = true;
}
}
}
var _valid2 = _errs31 === errors;
if(_valid2){
valid9 = true;
passing2 = 0;
var props2 = {};
props2.algorithm = true;
props2.hex = true;
}
const _errs34 = errors;
if(data13 && typeof data13 == "object" && !Array.isArray(data13)){
if(data13.algorithm !== undefined){
const _errs35 = errors;
if("sha256" !== data13.algorithm){
const err22 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err22];
}
else {
vErrors.push(err22);
}
errors++;
}
var valid11 = _errs35 === errors;
}
else {
var valid11 = true;
}
if(valid11){
if(data13.hex !== undefined){
let data17 = data13.hex;
const _errs36 = errors;
if(typeof data17 === "string"){
if(!pattern5.test(data17)){
const err23 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err23];
}
else {
vErrors.push(err23);
}
errors++;
}
}
var valid11 = _errs36 === errors;
}
else {
var valid11 = true;
}
}
}
var _valid2 = _errs34 === errors;
if(_valid2 && valid9){
valid9 = false;
passing2 = [passing2, 1];
}
else {
if(_valid2){
valid9 = true;
passing2 = 1;
if(props2 !== true){
props2 = props2 || {};
props2.algorithm = true;
props2.hex = true;
}
}
}
if(!valid9){
const err24 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing2},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err24];
}
else {
vErrors.push(err24);
}
errors++;
}
else {
errors = _errs30;
if(vErrors !== null){
if(_errs30){
vErrors.length = _errs30;
}
else {
vErrors = null;
}
}
}
if(errors === _errs28){
if(data13 && typeof data13 == "object" && !Array.isArray(data13)){
let missing3;
if(((data13.algorithm === undefined) && (missing3 = "algorithm")) || ((data13.hex === undefined) && (missing3 = "hex"))){
const err25 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing3},message:"must have required property '"+missing3+"'"};
if(vErrors === null){
vErrors = [err25];
}
else {
vErrors.push(err25);
}
errors++;
}
else {
const _errs37 = errors;
for(const key3 in data13){
if(!((key3 === "algorithm") || (key3 === "hex"))){
const err26 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key3},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err26];
}
else {
vErrors.push(err26);
}
errors++;
break;
}
}
if(_errs37 === errors){
if(data13.algorithm !== undefined){
let data18 = data13.algorithm;
const _errs38 = errors;
if(!((data18 === "sha1") || (data18 === "sha256"))){
const err27 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err27];
}
else {
vErrors.push(err27);
}
errors++;
}
var valid12 = _errs38 === errors;
}
else {
var valid12 = true;
}
if(valid12){
if(data13.hex !== undefined){
const _errs39 = errors;
if(typeof data13.hex !== "string"){
const err28 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err28];
}
else {
vErrors.push(err28);
}
errors++;
}
var valid12 = _errs39 === errors;
}
else {
var valid12 = true;
}
}
}
}
}
else {
const err29 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err29];
}
else {
vErrors.push(err29);
}
errors++;
}
}
var valid7 = _errs27 === errors;
}
else {
var valid7 = true;
}
if(valid7){
if(data.operation !== undefined){
const _errs41 = errors;
if("list_documents" !== data.operation){
const err30 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/1/properties/operation/const",keyword:"const",params:{allowedValue: "list_documents"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err30];
}
else {
vErrors.push(err30);
}
errors++;
}
var valid7 = _errs41 === errors;
}
else {
var valid7 = true;
}
if(valid7){
if(data.data !== undefined){
const _errs42 = errors;
if(!(validate22(data.data, {instancePath:instancePath+"/data",parentData:data,parentDataProperty:"data",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate22.errors : vErrors.concat(validate22.errors);
errors = vErrors.length;
}
var valid7 = _errs42 === errors;
}
else {
var valid7 = true;
}
}
}
}
}
}
}
}
else {
const err31 = {instancePath,schemaPath:"#/oneOf/1/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err31];
}
else {
vErrors.push(err31);
}
errors++;
}
}
var _valid0 = _errs22 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 1];
}
else {
if(_valid0){
valid0 = true;
passing0 = 1;
if(props1 !== true){
props1 = true;
}
}
const _errs43 = errors;
if(errors === _errs43){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing4;
if((((((data.formatVersion === undefined) && (missing4 = "formatVersion")) || ((data.kind === undefined) && (missing4 = "kind"))) || ((data.basis === undefined) && (missing4 = "basis"))) || ((data.operation === undefined) && (missing4 = "operation"))) || ((data.data === undefined) && (missing4 = "data"))){
const err32 = {instancePath,schemaPath:"#/oneOf/2/required",keyword:"required",params:{missingProperty: missing4},message:"must have required property '"+missing4+"'"};
if(vErrors === null){
vErrors = [err32];
}
else {
vErrors.push(err32);
}
errors++;
}
else {
const _errs45 = errors;
for(const key4 in data){
if(!(((((key4 === "formatVersion") || (key4 === "kind")) || (key4 === "basis")) || (key4 === "operation")) || (key4 === "data"))){
const err33 = {instancePath,schemaPath:"#/oneOf/2/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key4},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err33];
}
else {
vErrors.push(err33);
}
errors++;
break;
}
}
if(_errs45 === errors){
if(data.formatVersion !== undefined){
const _errs46 = errors;
if(1 !== data.formatVersion){
const err34 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/2/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err34];
}
else {
vErrors.push(err34);
}
errors++;
}
var valid13 = _errs46 === errors;
}
else {
var valid13 = true;
}
if(valid13){
if(data.kind !== undefined){
const _errs47 = errors;
if("read" !== data.kind){
const err35 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/2/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err35];
}
else {
vErrors.push(err35);
}
errors++;
}
var valid13 = _errs47 === errors;
}
else {
var valid13 = true;
}
if(valid13){
if(data.basis !== undefined){
let data24 = data.basis;
const _errs48 = errors;
const _errs49 = errors;
const _errs51 = errors;
let valid15 = false;
let passing3 = null;
const _errs52 = errors;
if(data24 && typeof data24 == "object" && !Array.isArray(data24)){
if(data24.algorithm !== undefined){
const _errs53 = errors;
if("sha1" !== data24.algorithm){
const err36 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err36];
}
else {
vErrors.push(err36);
}
errors++;
}
var valid16 = _errs53 === errors;
}
else {
var valid16 = true;
}
if(valid16){
if(data24.hex !== undefined){
let data26 = data24.hex;
const _errs54 = errors;
if(typeof data26 === "string"){
if(!pattern4.test(data26)){
const err37 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err37];
}
else {
vErrors.push(err37);
}
errors++;
}
}
var valid16 = _errs54 === errors;
}
else {
var valid16 = true;
}
}
}
var _valid3 = _errs52 === errors;
if(_valid3){
valid15 = true;
passing3 = 0;
var props3 = {};
props3.algorithm = true;
props3.hex = true;
}
const _errs55 = errors;
if(data24 && typeof data24 == "object" && !Array.isArray(data24)){
if(data24.algorithm !== undefined){
const _errs56 = errors;
if("sha256" !== data24.algorithm){
const err38 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err38];
}
else {
vErrors.push(err38);
}
errors++;
}
var valid17 = _errs56 === errors;
}
else {
var valid17 = true;
}
if(valid17){
if(data24.hex !== undefined){
let data28 = data24.hex;
const _errs57 = errors;
if(typeof data28 === "string"){
if(!pattern5.test(data28)){
const err39 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err39];
}
else {
vErrors.push(err39);
}
errors++;
}
}
var valid17 = _errs57 === errors;
}
else {
var valid17 = true;
}
}
}
var _valid3 = _errs55 === errors;
if(_valid3 && valid15){
valid15 = false;
passing3 = [passing3, 1];
}
else {
if(_valid3){
valid15 = true;
passing3 = 1;
if(props3 !== true){
props3 = props3 || {};
props3.algorithm = true;
props3.hex = true;
}
}
}
if(!valid15){
const err40 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing3},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err40];
}
else {
vErrors.push(err40);
}
errors++;
}
else {
errors = _errs51;
if(vErrors !== null){
if(_errs51){
vErrors.length = _errs51;
}
else {
vErrors = null;
}
}
}
if(errors === _errs49){
if(data24 && typeof data24 == "object" && !Array.isArray(data24)){
let missing5;
if(((data24.algorithm === undefined) && (missing5 = "algorithm")) || ((data24.hex === undefined) && (missing5 = "hex"))){
const err41 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing5},message:"must have required property '"+missing5+"'"};
if(vErrors === null){
vErrors = [err41];
}
else {
vErrors.push(err41);
}
errors++;
}
else {
const _errs58 = errors;
for(const key5 in data24){
if(!((key5 === "algorithm") || (key5 === "hex"))){
const err42 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key5},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err42];
}
else {
vErrors.push(err42);
}
errors++;
break;
}
}
if(_errs58 === errors){
if(data24.algorithm !== undefined){
let data29 = data24.algorithm;
const _errs59 = errors;
if(!((data29 === "sha1") || (data29 === "sha256"))){
const err43 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err43];
}
else {
vErrors.push(err43);
}
errors++;
}
var valid18 = _errs59 === errors;
}
else {
var valid18 = true;
}
if(valid18){
if(data24.hex !== undefined){
const _errs60 = errors;
if(typeof data24.hex !== "string"){
const err44 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err44];
}
else {
vErrors.push(err44);
}
errors++;
}
var valid18 = _errs60 === errors;
}
else {
var valid18 = true;
}
}
}
}
}
else {
const err45 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err45];
}
else {
vErrors.push(err45);
}
errors++;
}
}
var valid13 = _errs48 === errors;
}
else {
var valid13 = true;
}
if(valid13){
if(data.operation !== undefined){
const _errs62 = errors;
if("list_revisions" !== data.operation){
const err46 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/2/properties/operation/const",keyword:"const",params:{allowedValue: "list_revisions"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err46];
}
else {
vErrors.push(err46);
}
errors++;
}
var valid13 = _errs62 === errors;
}
else {
var valid13 = true;
}
if(valid13){
if(data.data !== undefined){
const _errs63 = errors;
if(!(validate22(data.data, {instancePath:instancePath+"/data",parentData:data,parentDataProperty:"data",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate22.errors : vErrors.concat(validate22.errors);
errors = vErrors.length;
}
var valid13 = _errs63 === errors;
}
else {
var valid13 = true;
}
}
}
}
}
}
}
}
else {
const err47 = {instancePath,schemaPath:"#/oneOf/2/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err47];
}
else {
vErrors.push(err47);
}
errors++;
}
}
var _valid0 = _errs43 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 2];
}
else {
if(_valid0){
valid0 = true;
passing0 = 2;
if(props1 !== true){
props1 = true;
}
}
const _errs64 = errors;
if(errors === _errs64){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing6;
if((((((data.formatVersion === undefined) && (missing6 = "formatVersion")) || ((data.kind === undefined) && (missing6 = "kind"))) || ((data.basis === undefined) && (missing6 = "basis"))) || ((data.operation === undefined) && (missing6 = "operation"))) || ((data.data === undefined) && (missing6 = "data"))){
const err48 = {instancePath,schemaPath:"#/oneOf/3/required",keyword:"required",params:{missingProperty: missing6},message:"must have required property '"+missing6+"'"};
if(vErrors === null){
vErrors = [err48];
}
else {
vErrors.push(err48);
}
errors++;
}
else {
const _errs66 = errors;
for(const key6 in data){
if(!(((((key6 === "formatVersion") || (key6 === "kind")) || (key6 === "basis")) || (key6 === "operation")) || (key6 === "data"))){
const err49 = {instancePath,schemaPath:"#/oneOf/3/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key6},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err49];
}
else {
vErrors.push(err49);
}
errors++;
break;
}
}
if(_errs66 === errors){
if(data.formatVersion !== undefined){
const _errs67 = errors;
if(1 !== data.formatVersion){
const err50 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/3/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err50];
}
else {
vErrors.push(err50);
}
errors++;
}
var valid19 = _errs67 === errors;
}
else {
var valid19 = true;
}
if(valid19){
if(data.kind !== undefined){
const _errs68 = errors;
if("read" !== data.kind){
const err51 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/3/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err51];
}
else {
vErrors.push(err51);
}
errors++;
}
var valid19 = _errs68 === errors;
}
else {
var valid19 = true;
}
if(valid19){
if(data.basis !== undefined){
let data35 = data.basis;
const _errs69 = errors;
const _errs70 = errors;
const _errs72 = errors;
let valid21 = false;
let passing4 = null;
const _errs73 = errors;
if(data35 && typeof data35 == "object" && !Array.isArray(data35)){
if(data35.algorithm !== undefined){
const _errs74 = errors;
if("sha1" !== data35.algorithm){
const err52 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err52];
}
else {
vErrors.push(err52);
}
errors++;
}
var valid22 = _errs74 === errors;
}
else {
var valid22 = true;
}
if(valid22){
if(data35.hex !== undefined){
let data37 = data35.hex;
const _errs75 = errors;
if(typeof data37 === "string"){
if(!pattern4.test(data37)){
const err53 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err53];
}
else {
vErrors.push(err53);
}
errors++;
}
}
var valid22 = _errs75 === errors;
}
else {
var valid22 = true;
}
}
}
var _valid4 = _errs73 === errors;
if(_valid4){
valid21 = true;
passing4 = 0;
var props4 = {};
props4.algorithm = true;
props4.hex = true;
}
const _errs76 = errors;
if(data35 && typeof data35 == "object" && !Array.isArray(data35)){
if(data35.algorithm !== undefined){
const _errs77 = errors;
if("sha256" !== data35.algorithm){
const err54 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err54];
}
else {
vErrors.push(err54);
}
errors++;
}
var valid23 = _errs77 === errors;
}
else {
var valid23 = true;
}
if(valid23){
if(data35.hex !== undefined){
let data39 = data35.hex;
const _errs78 = errors;
if(typeof data39 === "string"){
if(!pattern5.test(data39)){
const err55 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err55];
}
else {
vErrors.push(err55);
}
errors++;
}
}
var valid23 = _errs78 === errors;
}
else {
var valid23 = true;
}
}
}
var _valid4 = _errs76 === errors;
if(_valid4 && valid21){
valid21 = false;
passing4 = [passing4, 1];
}
else {
if(_valid4){
valid21 = true;
passing4 = 1;
if(props4 !== true){
props4 = props4 || {};
props4.algorithm = true;
props4.hex = true;
}
}
}
if(!valid21){
const err56 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing4},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err56];
}
else {
vErrors.push(err56);
}
errors++;
}
else {
errors = _errs72;
if(vErrors !== null){
if(_errs72){
vErrors.length = _errs72;
}
else {
vErrors = null;
}
}
}
if(errors === _errs70){
if(data35 && typeof data35 == "object" && !Array.isArray(data35)){
let missing7;
if(((data35.algorithm === undefined) && (missing7 = "algorithm")) || ((data35.hex === undefined) && (missing7 = "hex"))){
const err57 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing7},message:"must have required property '"+missing7+"'"};
if(vErrors === null){
vErrors = [err57];
}
else {
vErrors.push(err57);
}
errors++;
}
else {
const _errs79 = errors;
for(const key7 in data35){
if(!((key7 === "algorithm") || (key7 === "hex"))){
const err58 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key7},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err58];
}
else {
vErrors.push(err58);
}
errors++;
break;
}
}
if(_errs79 === errors){
if(data35.algorithm !== undefined){
let data40 = data35.algorithm;
const _errs80 = errors;
if(!((data40 === "sha1") || (data40 === "sha256"))){
const err59 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err59];
}
else {
vErrors.push(err59);
}
errors++;
}
var valid24 = _errs80 === errors;
}
else {
var valid24 = true;
}
if(valid24){
if(data35.hex !== undefined){
const _errs81 = errors;
if(typeof data35.hex !== "string"){
const err60 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err60];
}
else {
vErrors.push(err60);
}
errors++;
}
var valid24 = _errs81 === errors;
}
else {
var valid24 = true;
}
}
}
}
}
else {
const err61 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err61];
}
else {
vErrors.push(err61);
}
errors++;
}
}
var valid19 = _errs69 === errors;
}
else {
var valid19 = true;
}
if(valid19){
if(data.operation !== undefined){
const _errs83 = errors;
if("list_requests" !== data.operation){
const err62 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/3/properties/operation/const",keyword:"const",params:{allowedValue: "list_requests"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err62];
}
else {
vErrors.push(err62);
}
errors++;
}
var valid19 = _errs83 === errors;
}
else {
var valid19 = true;
}
if(valid19){
if(data.data !== undefined){
const _errs84 = errors;
if(!(validate22(data.data, {instancePath:instancePath+"/data",parentData:data,parentDataProperty:"data",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate22.errors : vErrors.concat(validate22.errors);
errors = vErrors.length;
}
var valid19 = _errs84 === errors;
}
else {
var valid19 = true;
}
}
}
}
}
}
}
}
else {
const err63 = {instancePath,schemaPath:"#/oneOf/3/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err63];
}
else {
vErrors.push(err63);
}
errors++;
}
}
var _valid0 = _errs64 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 3];
}
else {
if(_valid0){
valid0 = true;
passing0 = 3;
if(props1 !== true){
props1 = true;
}
}
const _errs85 = errors;
if(errors === _errs85){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing8;
if((((((data.formatVersion === undefined) && (missing8 = "formatVersion")) || ((data.kind === undefined) && (missing8 = "kind"))) || ((data.basis === undefined) && (missing8 = "basis"))) || ((data.operation === undefined) && (missing8 = "operation"))) || ((data.data === undefined) && (missing8 = "data"))){
const err64 = {instancePath,schemaPath:"#/oneOf/4/required",keyword:"required",params:{missingProperty: missing8},message:"must have required property '"+missing8+"'"};
if(vErrors === null){
vErrors = [err64];
}
else {
vErrors.push(err64);
}
errors++;
}
else {
const _errs87 = errors;
for(const key8 in data){
if(!(((((key8 === "formatVersion") || (key8 === "kind")) || (key8 === "basis")) || (key8 === "operation")) || (key8 === "data"))){
const err65 = {instancePath,schemaPath:"#/oneOf/4/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key8},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err65];
}
else {
vErrors.push(err65);
}
errors++;
break;
}
}
if(_errs87 === errors){
if(data.formatVersion !== undefined){
const _errs88 = errors;
if(1 !== data.formatVersion){
const err66 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/4/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err66];
}
else {
vErrors.push(err66);
}
errors++;
}
var valid25 = _errs88 === errors;
}
else {
var valid25 = true;
}
if(valid25){
if(data.kind !== undefined){
const _errs89 = errors;
if("read" !== data.kind){
const err67 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/4/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err67];
}
else {
vErrors.push(err67);
}
errors++;
}
var valid25 = _errs89 === errors;
}
else {
var valid25 = true;
}
if(valid25){
if(data.basis !== undefined){
let data46 = data.basis;
const _errs90 = errors;
const _errs91 = errors;
const _errs93 = errors;
let valid27 = false;
let passing5 = null;
const _errs94 = errors;
if(data46 && typeof data46 == "object" && !Array.isArray(data46)){
if(data46.algorithm !== undefined){
const _errs95 = errors;
if("sha1" !== data46.algorithm){
const err68 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err68];
}
else {
vErrors.push(err68);
}
errors++;
}
var valid28 = _errs95 === errors;
}
else {
var valid28 = true;
}
if(valid28){
if(data46.hex !== undefined){
let data48 = data46.hex;
const _errs96 = errors;
if(typeof data48 === "string"){
if(!pattern4.test(data48)){
const err69 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err69];
}
else {
vErrors.push(err69);
}
errors++;
}
}
var valid28 = _errs96 === errors;
}
else {
var valid28 = true;
}
}
}
var _valid5 = _errs94 === errors;
if(_valid5){
valid27 = true;
passing5 = 0;
var props5 = {};
props5.algorithm = true;
props5.hex = true;
}
const _errs97 = errors;
if(data46 && typeof data46 == "object" && !Array.isArray(data46)){
if(data46.algorithm !== undefined){
const _errs98 = errors;
if("sha256" !== data46.algorithm){
const err70 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err70];
}
else {
vErrors.push(err70);
}
errors++;
}
var valid29 = _errs98 === errors;
}
else {
var valid29 = true;
}
if(valid29){
if(data46.hex !== undefined){
let data50 = data46.hex;
const _errs99 = errors;
if(typeof data50 === "string"){
if(!pattern5.test(data50)){
const err71 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err71];
}
else {
vErrors.push(err71);
}
errors++;
}
}
var valid29 = _errs99 === errors;
}
else {
var valid29 = true;
}
}
}
var _valid5 = _errs97 === errors;
if(_valid5 && valid27){
valid27 = false;
passing5 = [passing5, 1];
}
else {
if(_valid5){
valid27 = true;
passing5 = 1;
if(props5 !== true){
props5 = props5 || {};
props5.algorithm = true;
props5.hex = true;
}
}
}
if(!valid27){
const err72 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing5},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err72];
}
else {
vErrors.push(err72);
}
errors++;
}
else {
errors = _errs93;
if(vErrors !== null){
if(_errs93){
vErrors.length = _errs93;
}
else {
vErrors = null;
}
}
}
if(errors === _errs91){
if(data46 && typeof data46 == "object" && !Array.isArray(data46)){
let missing9;
if(((data46.algorithm === undefined) && (missing9 = "algorithm")) || ((data46.hex === undefined) && (missing9 = "hex"))){
const err73 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing9},message:"must have required property '"+missing9+"'"};
if(vErrors === null){
vErrors = [err73];
}
else {
vErrors.push(err73);
}
errors++;
}
else {
const _errs100 = errors;
for(const key9 in data46){
if(!((key9 === "algorithm") || (key9 === "hex"))){
const err74 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key9},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err74];
}
else {
vErrors.push(err74);
}
errors++;
break;
}
}
if(_errs100 === errors){
if(data46.algorithm !== undefined){
let data51 = data46.algorithm;
const _errs101 = errors;
if(!((data51 === "sha1") || (data51 === "sha256"))){
const err75 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err75];
}
else {
vErrors.push(err75);
}
errors++;
}
var valid30 = _errs101 === errors;
}
else {
var valid30 = true;
}
if(valid30){
if(data46.hex !== undefined){
const _errs102 = errors;
if(typeof data46.hex !== "string"){
const err76 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err76];
}
else {
vErrors.push(err76);
}
errors++;
}
var valid30 = _errs102 === errors;
}
else {
var valid30 = true;
}
}
}
}
}
else {
const err77 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err77];
}
else {
vErrors.push(err77);
}
errors++;
}
}
var valid25 = _errs90 === errors;
}
else {
var valid25 = true;
}
if(valid25){
if(data.operation !== undefined){
const _errs104 = errors;
if("list_manifests" !== data.operation){
const err78 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/4/properties/operation/const",keyword:"const",params:{allowedValue: "list_manifests"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err78];
}
else {
vErrors.push(err78);
}
errors++;
}
var valid25 = _errs104 === errors;
}
else {
var valid25 = true;
}
if(valid25){
if(data.data !== undefined){
const _errs105 = errors;
if(!(validate22(data.data, {instancePath:instancePath+"/data",parentData:data,parentDataProperty:"data",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate22.errors : vErrors.concat(validate22.errors);
errors = vErrors.length;
}
var valid25 = _errs105 === errors;
}
else {
var valid25 = true;
}
}
}
}
}
}
}
}
else {
const err79 = {instancePath,schemaPath:"#/oneOf/4/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err79];
}
else {
vErrors.push(err79);
}
errors++;
}
}
var _valid0 = _errs85 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 4];
}
else {
if(_valid0){
valid0 = true;
passing0 = 4;
if(props1 !== true){
props1 = true;
}
}
const _errs106 = errors;
if(errors === _errs106){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing10;
if((((((data.formatVersion === undefined) && (missing10 = "formatVersion")) || ((data.kind === undefined) && (missing10 = "kind"))) || ((data.basis === undefined) && (missing10 = "basis"))) || ((data.operation === undefined) && (missing10 = "operation"))) || ((data.data === undefined) && (missing10 = "data"))){
const err80 = {instancePath,schemaPath:"#/oneOf/5/required",keyword:"required",params:{missingProperty: missing10},message:"must have required property '"+missing10+"'"};
if(vErrors === null){
vErrors = [err80];
}
else {
vErrors.push(err80);
}
errors++;
}
else {
const _errs108 = errors;
for(const key10 in data){
if(!(((((key10 === "formatVersion") || (key10 === "kind")) || (key10 === "basis")) || (key10 === "operation")) || (key10 === "data"))){
const err81 = {instancePath,schemaPath:"#/oneOf/5/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key10},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err81];
}
else {
vErrors.push(err81);
}
errors++;
break;
}
}
if(_errs108 === errors){
if(data.formatVersion !== undefined){
const _errs109 = errors;
if(1 !== data.formatVersion){
const err82 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/5/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err82];
}
else {
vErrors.push(err82);
}
errors++;
}
var valid31 = _errs109 === errors;
}
else {
var valid31 = true;
}
if(valid31){
if(data.kind !== undefined){
const _errs110 = errors;
if("read" !== data.kind){
const err83 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/5/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err83];
}
else {
vErrors.push(err83);
}
errors++;
}
var valid31 = _errs110 === errors;
}
else {
var valid31 = true;
}
if(valid31){
if(data.basis !== undefined){
let data57 = data.basis;
const _errs111 = errors;
const _errs112 = errors;
const _errs114 = errors;
let valid33 = false;
let passing6 = null;
const _errs115 = errors;
if(data57 && typeof data57 == "object" && !Array.isArray(data57)){
if(data57.algorithm !== undefined){
const _errs116 = errors;
if("sha1" !== data57.algorithm){
const err84 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err84];
}
else {
vErrors.push(err84);
}
errors++;
}
var valid34 = _errs116 === errors;
}
else {
var valid34 = true;
}
if(valid34){
if(data57.hex !== undefined){
let data59 = data57.hex;
const _errs117 = errors;
if(typeof data59 === "string"){
if(!pattern4.test(data59)){
const err85 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err85];
}
else {
vErrors.push(err85);
}
errors++;
}
}
var valid34 = _errs117 === errors;
}
else {
var valid34 = true;
}
}
}
var _valid6 = _errs115 === errors;
if(_valid6){
valid33 = true;
passing6 = 0;
var props6 = {};
props6.algorithm = true;
props6.hex = true;
}
const _errs118 = errors;
if(data57 && typeof data57 == "object" && !Array.isArray(data57)){
if(data57.algorithm !== undefined){
const _errs119 = errors;
if("sha256" !== data57.algorithm){
const err86 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err86];
}
else {
vErrors.push(err86);
}
errors++;
}
var valid35 = _errs119 === errors;
}
else {
var valid35 = true;
}
if(valid35){
if(data57.hex !== undefined){
let data61 = data57.hex;
const _errs120 = errors;
if(typeof data61 === "string"){
if(!pattern5.test(data61)){
const err87 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err87];
}
else {
vErrors.push(err87);
}
errors++;
}
}
var valid35 = _errs120 === errors;
}
else {
var valid35 = true;
}
}
}
var _valid6 = _errs118 === errors;
if(_valid6 && valid33){
valid33 = false;
passing6 = [passing6, 1];
}
else {
if(_valid6){
valid33 = true;
passing6 = 1;
if(props6 !== true){
props6 = props6 || {};
props6.algorithm = true;
props6.hex = true;
}
}
}
if(!valid33){
const err88 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing6},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err88];
}
else {
vErrors.push(err88);
}
errors++;
}
else {
errors = _errs114;
if(vErrors !== null){
if(_errs114){
vErrors.length = _errs114;
}
else {
vErrors = null;
}
}
}
if(errors === _errs112){
if(data57 && typeof data57 == "object" && !Array.isArray(data57)){
let missing11;
if(((data57.algorithm === undefined) && (missing11 = "algorithm")) || ((data57.hex === undefined) && (missing11 = "hex"))){
const err89 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing11},message:"must have required property '"+missing11+"'"};
if(vErrors === null){
vErrors = [err89];
}
else {
vErrors.push(err89);
}
errors++;
}
else {
const _errs121 = errors;
for(const key11 in data57){
if(!((key11 === "algorithm") || (key11 === "hex"))){
const err90 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key11},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err90];
}
else {
vErrors.push(err90);
}
errors++;
break;
}
}
if(_errs121 === errors){
if(data57.algorithm !== undefined){
let data62 = data57.algorithm;
const _errs122 = errors;
if(!((data62 === "sha1") || (data62 === "sha256"))){
const err91 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err91];
}
else {
vErrors.push(err91);
}
errors++;
}
var valid36 = _errs122 === errors;
}
else {
var valid36 = true;
}
if(valid36){
if(data57.hex !== undefined){
const _errs123 = errors;
if(typeof data57.hex !== "string"){
const err92 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err92];
}
else {
vErrors.push(err92);
}
errors++;
}
var valid36 = _errs123 === errors;
}
else {
var valid36 = true;
}
}
}
}
}
else {
const err93 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err93];
}
else {
vErrors.push(err93);
}
errors++;
}
}
var valid31 = _errs111 === errors;
}
else {
var valid31 = true;
}
if(valid31){
if(data.operation !== undefined){
const _errs125 = errors;
if("read_revision" !== data.operation){
const err94 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/5/properties/operation/const",keyword:"const",params:{allowedValue: "read_revision"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err94];
}
else {
vErrors.push(err94);
}
errors++;
}
var valid31 = _errs125 === errors;
}
else {
var valid31 = true;
}
if(valid31){
if(data.data !== undefined){
const _errs126 = errors;
if(!(validate35(data.data, {instancePath:instancePath+"/data",parentData:data,parentDataProperty:"data",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate35.errors : vErrors.concat(validate35.errors);
errors = vErrors.length;
}
var valid31 = _errs126 === errors;
}
else {
var valid31 = true;
}
}
}
}
}
}
}
}
else {
const err95 = {instancePath,schemaPath:"#/oneOf/5/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err95];
}
else {
vErrors.push(err95);
}
errors++;
}
}
var _valid0 = _errs106 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 5];
}
else {
if(_valid0){
valid0 = true;
passing0 = 5;
if(props1 !== true){
props1 = true;
}
}
const _errs127 = errors;
if(errors === _errs127){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing12;
if((((((data.formatVersion === undefined) && (missing12 = "formatVersion")) || ((data.kind === undefined) && (missing12 = "kind"))) || ((data.basis === undefined) && (missing12 = "basis"))) || ((data.operation === undefined) && (missing12 = "operation"))) || ((data.data === undefined) && (missing12 = "data"))){
const err96 = {instancePath,schemaPath:"#/oneOf/6/required",keyword:"required",params:{missingProperty: missing12},message:"must have required property '"+missing12+"'"};
if(vErrors === null){
vErrors = [err96];
}
else {
vErrors.push(err96);
}
errors++;
}
else {
const _errs129 = errors;
for(const key12 in data){
if(!(((((key12 === "formatVersion") || (key12 === "kind")) || (key12 === "basis")) || (key12 === "operation")) || (key12 === "data"))){
const err97 = {instancePath,schemaPath:"#/oneOf/6/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key12},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err97];
}
else {
vErrors.push(err97);
}
errors++;
break;
}
}
if(_errs129 === errors){
if(data.formatVersion !== undefined){
const _errs130 = errors;
if(1 !== data.formatVersion){
const err98 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/6/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err98];
}
else {
vErrors.push(err98);
}
errors++;
}
var valid37 = _errs130 === errors;
}
else {
var valid37 = true;
}
if(valid37){
if(data.kind !== undefined){
const _errs131 = errors;
if("read" !== data.kind){
const err99 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/6/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err99];
}
else {
vErrors.push(err99);
}
errors++;
}
var valid37 = _errs131 === errors;
}
else {
var valid37 = true;
}
if(valid37){
if(data.basis !== undefined){
let data68 = data.basis;
const _errs132 = errors;
const _errs133 = errors;
const _errs135 = errors;
let valid39 = false;
let passing7 = null;
const _errs136 = errors;
if(data68 && typeof data68 == "object" && !Array.isArray(data68)){
if(data68.algorithm !== undefined){
const _errs137 = errors;
if("sha1" !== data68.algorithm){
const err100 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err100];
}
else {
vErrors.push(err100);
}
errors++;
}
var valid40 = _errs137 === errors;
}
else {
var valid40 = true;
}
if(valid40){
if(data68.hex !== undefined){
let data70 = data68.hex;
const _errs138 = errors;
if(typeof data70 === "string"){
if(!pattern4.test(data70)){
const err101 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err101];
}
else {
vErrors.push(err101);
}
errors++;
}
}
var valid40 = _errs138 === errors;
}
else {
var valid40 = true;
}
}
}
var _valid7 = _errs136 === errors;
if(_valid7){
valid39 = true;
passing7 = 0;
var props7 = {};
props7.algorithm = true;
props7.hex = true;
}
const _errs139 = errors;
if(data68 && typeof data68 == "object" && !Array.isArray(data68)){
if(data68.algorithm !== undefined){
const _errs140 = errors;
if("sha256" !== data68.algorithm){
const err102 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err102];
}
else {
vErrors.push(err102);
}
errors++;
}
var valid41 = _errs140 === errors;
}
else {
var valid41 = true;
}
if(valid41){
if(data68.hex !== undefined){
let data72 = data68.hex;
const _errs141 = errors;
if(typeof data72 === "string"){
if(!pattern5.test(data72)){
const err103 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err103];
}
else {
vErrors.push(err103);
}
errors++;
}
}
var valid41 = _errs141 === errors;
}
else {
var valid41 = true;
}
}
}
var _valid7 = _errs139 === errors;
if(_valid7 && valid39){
valid39 = false;
passing7 = [passing7, 1];
}
else {
if(_valid7){
valid39 = true;
passing7 = 1;
if(props7 !== true){
props7 = props7 || {};
props7.algorithm = true;
props7.hex = true;
}
}
}
if(!valid39){
const err104 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing7},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err104];
}
else {
vErrors.push(err104);
}
errors++;
}
else {
errors = _errs135;
if(vErrors !== null){
if(_errs135){
vErrors.length = _errs135;
}
else {
vErrors = null;
}
}
}
if(errors === _errs133){
if(data68 && typeof data68 == "object" && !Array.isArray(data68)){
let missing13;
if(((data68.algorithm === undefined) && (missing13 = "algorithm")) || ((data68.hex === undefined) && (missing13 = "hex"))){
const err105 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing13},message:"must have required property '"+missing13+"'"};
if(vErrors === null){
vErrors = [err105];
}
else {
vErrors.push(err105);
}
errors++;
}
else {
const _errs142 = errors;
for(const key13 in data68){
if(!((key13 === "algorithm") || (key13 === "hex"))){
const err106 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key13},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err106];
}
else {
vErrors.push(err106);
}
errors++;
break;
}
}
if(_errs142 === errors){
if(data68.algorithm !== undefined){
let data73 = data68.algorithm;
const _errs143 = errors;
if(!((data73 === "sha1") || (data73 === "sha256"))){
const err107 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err107];
}
else {
vErrors.push(err107);
}
errors++;
}
var valid42 = _errs143 === errors;
}
else {
var valid42 = true;
}
if(valid42){
if(data68.hex !== undefined){
const _errs144 = errors;
if(typeof data68.hex !== "string"){
const err108 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err108];
}
else {
vErrors.push(err108);
}
errors++;
}
var valid42 = _errs144 === errors;
}
else {
var valid42 = true;
}
}
}
}
}
else {
const err109 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err109];
}
else {
vErrors.push(err109);
}
errors++;
}
}
var valid37 = _errs132 === errors;
}
else {
var valid37 = true;
}
if(valid37){
if(data.operation !== undefined){
const _errs146 = errors;
if("read_clause" !== data.operation){
const err110 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/6/properties/operation/const",keyword:"const",params:{allowedValue: "read_clause"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err110];
}
else {
vErrors.push(err110);
}
errors++;
}
var valid37 = _errs146 === errors;
}
else {
var valid37 = true;
}
if(valid37){
if(data.data !== undefined){
const _errs147 = errors;
if(!(validate45(data.data, {instancePath:instancePath+"/data",parentData:data,parentDataProperty:"data",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate45.errors : vErrors.concat(validate45.errors);
errors = vErrors.length;
}
var valid37 = _errs147 === errors;
}
else {
var valid37 = true;
}
}
}
}
}
}
}
}
else {
const err111 = {instancePath,schemaPath:"#/oneOf/6/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err111];
}
else {
vErrors.push(err111);
}
errors++;
}
}
var _valid0 = _errs127 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 6];
}
else {
if(_valid0){
valid0 = true;
passing0 = 6;
if(props1 !== true){
props1 = true;
}
}
const _errs148 = errors;
if(errors === _errs148){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing14;
if((((((data.formatVersion === undefined) && (missing14 = "formatVersion")) || ((data.kind === undefined) && (missing14 = "kind"))) || ((data.basis === undefined) && (missing14 = "basis"))) || ((data.operation === undefined) && (missing14 = "operation"))) || ((data.data === undefined) && (missing14 = "data"))){
const err112 = {instancePath,schemaPath:"#/oneOf/7/required",keyword:"required",params:{missingProperty: missing14},message:"must have required property '"+missing14+"'"};
if(vErrors === null){
vErrors = [err112];
}
else {
vErrors.push(err112);
}
errors++;
}
else {
const _errs150 = errors;
for(const key14 in data){
if(!(((((key14 === "formatVersion") || (key14 === "kind")) || (key14 === "basis")) || (key14 === "operation")) || (key14 === "data"))){
const err113 = {instancePath,schemaPath:"#/oneOf/7/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key14},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err113];
}
else {
vErrors.push(err113);
}
errors++;
break;
}
}
if(_errs150 === errors){
if(data.formatVersion !== undefined){
const _errs151 = errors;
if(1 !== data.formatVersion){
const err114 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/7/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err114];
}
else {
vErrors.push(err114);
}
errors++;
}
var valid43 = _errs151 === errors;
}
else {
var valid43 = true;
}
if(valid43){
if(data.kind !== undefined){
const _errs152 = errors;
if("read" !== data.kind){
const err115 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/7/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err115];
}
else {
vErrors.push(err115);
}
errors++;
}
var valid43 = _errs152 === errors;
}
else {
var valid43 = true;
}
if(valid43){
if(data.basis !== undefined){
let data79 = data.basis;
const _errs153 = errors;
const _errs154 = errors;
const _errs156 = errors;
let valid45 = false;
let passing8 = null;
const _errs157 = errors;
if(data79 && typeof data79 == "object" && !Array.isArray(data79)){
if(data79.algorithm !== undefined){
const _errs158 = errors;
if("sha1" !== data79.algorithm){
const err116 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err116];
}
else {
vErrors.push(err116);
}
errors++;
}
var valid46 = _errs158 === errors;
}
else {
var valid46 = true;
}
if(valid46){
if(data79.hex !== undefined){
let data81 = data79.hex;
const _errs159 = errors;
if(typeof data81 === "string"){
if(!pattern4.test(data81)){
const err117 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err117];
}
else {
vErrors.push(err117);
}
errors++;
}
}
var valid46 = _errs159 === errors;
}
else {
var valid46 = true;
}
}
}
var _valid8 = _errs157 === errors;
if(_valid8){
valid45 = true;
passing8 = 0;
var props8 = {};
props8.algorithm = true;
props8.hex = true;
}
const _errs160 = errors;
if(data79 && typeof data79 == "object" && !Array.isArray(data79)){
if(data79.algorithm !== undefined){
const _errs161 = errors;
if("sha256" !== data79.algorithm){
const err118 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err118];
}
else {
vErrors.push(err118);
}
errors++;
}
var valid47 = _errs161 === errors;
}
else {
var valid47 = true;
}
if(valid47){
if(data79.hex !== undefined){
let data83 = data79.hex;
const _errs162 = errors;
if(typeof data83 === "string"){
if(!pattern5.test(data83)){
const err119 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err119];
}
else {
vErrors.push(err119);
}
errors++;
}
}
var valid47 = _errs162 === errors;
}
else {
var valid47 = true;
}
}
}
var _valid8 = _errs160 === errors;
if(_valid8 && valid45){
valid45 = false;
passing8 = [passing8, 1];
}
else {
if(_valid8){
valid45 = true;
passing8 = 1;
if(props8 !== true){
props8 = props8 || {};
props8.algorithm = true;
props8.hex = true;
}
}
}
if(!valid45){
const err120 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing8},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err120];
}
else {
vErrors.push(err120);
}
errors++;
}
else {
errors = _errs156;
if(vErrors !== null){
if(_errs156){
vErrors.length = _errs156;
}
else {
vErrors = null;
}
}
}
if(errors === _errs154){
if(data79 && typeof data79 == "object" && !Array.isArray(data79)){
let missing15;
if(((data79.algorithm === undefined) && (missing15 = "algorithm")) || ((data79.hex === undefined) && (missing15 = "hex"))){
const err121 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing15},message:"must have required property '"+missing15+"'"};
if(vErrors === null){
vErrors = [err121];
}
else {
vErrors.push(err121);
}
errors++;
}
else {
const _errs163 = errors;
for(const key15 in data79){
if(!((key15 === "algorithm") || (key15 === "hex"))){
const err122 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key15},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err122];
}
else {
vErrors.push(err122);
}
errors++;
break;
}
}
if(_errs163 === errors){
if(data79.algorithm !== undefined){
let data84 = data79.algorithm;
const _errs164 = errors;
if(!((data84 === "sha1") || (data84 === "sha256"))){
const err123 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err123];
}
else {
vErrors.push(err123);
}
errors++;
}
var valid48 = _errs164 === errors;
}
else {
var valid48 = true;
}
if(valid48){
if(data79.hex !== undefined){
const _errs165 = errors;
if(typeof data79.hex !== "string"){
const err124 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err124];
}
else {
vErrors.push(err124);
}
errors++;
}
var valid48 = _errs165 === errors;
}
else {
var valid48 = true;
}
}
}
}
}
else {
const err125 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err125];
}
else {
vErrors.push(err125);
}
errors++;
}
}
var valid43 = _errs153 === errors;
}
else {
var valid43 = true;
}
if(valid43){
if(data.operation !== undefined){
const _errs167 = errors;
if("read_context" !== data.operation){
const err126 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/7/properties/operation/const",keyword:"const",params:{allowedValue: "read_context"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err126];
}
else {
vErrors.push(err126);
}
errors++;
}
var valid43 = _errs167 === errors;
}
else {
var valid43 = true;
}
if(valid43){
if(data.data !== undefined){
const _errs168 = errors;
if(!(validate45(data.data, {instancePath:instancePath+"/data",parentData:data,parentDataProperty:"data",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate45.errors : vErrors.concat(validate45.errors);
errors = vErrors.length;
}
var valid43 = _errs168 === errors;
}
else {
var valid43 = true;
}
}
}
}
}
}
}
}
else {
const err127 = {instancePath,schemaPath:"#/oneOf/7/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err127];
}
else {
vErrors.push(err127);
}
errors++;
}
}
var _valid0 = _errs148 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 7];
}
else {
if(_valid0){
valid0 = true;
passing0 = 7;
if(props1 !== true){
props1 = true;
}
}
const _errs169 = errors;
if(errors === _errs169){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing16;
if((((((data.formatVersion === undefined) && (missing16 = "formatVersion")) || ((data.kind === undefined) && (missing16 = "kind"))) || ((data.basis === undefined) && (missing16 = "basis"))) || ((data.operation === undefined) && (missing16 = "operation"))) || ((data.data === undefined) && (missing16 = "data"))){
const err128 = {instancePath,schemaPath:"#/oneOf/8/required",keyword:"required",params:{missingProperty: missing16},message:"must have required property '"+missing16+"'"};
if(vErrors === null){
vErrors = [err128];
}
else {
vErrors.push(err128);
}
errors++;
}
else {
const _errs171 = errors;
for(const key16 in data){
if(!(((((key16 === "formatVersion") || (key16 === "kind")) || (key16 === "basis")) || (key16 === "operation")) || (key16 === "data"))){
const err129 = {instancePath,schemaPath:"#/oneOf/8/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key16},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err129];
}
else {
vErrors.push(err129);
}
errors++;
break;
}
}
if(_errs171 === errors){
if(data.formatVersion !== undefined){
const _errs172 = errors;
if(1 !== data.formatVersion){
const err130 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/8/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err130];
}
else {
vErrors.push(err130);
}
errors++;
}
var valid49 = _errs172 === errors;
}
else {
var valid49 = true;
}
if(valid49){
if(data.kind !== undefined){
const _errs173 = errors;
if("read" !== data.kind){
const err131 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/8/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err131];
}
else {
vErrors.push(err131);
}
errors++;
}
var valid49 = _errs173 === errors;
}
else {
var valid49 = true;
}
if(valid49){
if(data.basis !== undefined){
let data90 = data.basis;
const _errs174 = errors;
const _errs175 = errors;
const _errs177 = errors;
let valid51 = false;
let passing9 = null;
const _errs178 = errors;
if(data90 && typeof data90 == "object" && !Array.isArray(data90)){
if(data90.algorithm !== undefined){
const _errs179 = errors;
if("sha1" !== data90.algorithm){
const err132 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err132];
}
else {
vErrors.push(err132);
}
errors++;
}
var valid52 = _errs179 === errors;
}
else {
var valid52 = true;
}
if(valid52){
if(data90.hex !== undefined){
let data92 = data90.hex;
const _errs180 = errors;
if(typeof data92 === "string"){
if(!pattern4.test(data92)){
const err133 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err133];
}
else {
vErrors.push(err133);
}
errors++;
}
}
var valid52 = _errs180 === errors;
}
else {
var valid52 = true;
}
}
}
var _valid9 = _errs178 === errors;
if(_valid9){
valid51 = true;
passing9 = 0;
var props9 = {};
props9.algorithm = true;
props9.hex = true;
}
const _errs181 = errors;
if(data90 && typeof data90 == "object" && !Array.isArray(data90)){
if(data90.algorithm !== undefined){
const _errs182 = errors;
if("sha256" !== data90.algorithm){
const err134 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err134];
}
else {
vErrors.push(err134);
}
errors++;
}
var valid53 = _errs182 === errors;
}
else {
var valid53 = true;
}
if(valid53){
if(data90.hex !== undefined){
let data94 = data90.hex;
const _errs183 = errors;
if(typeof data94 === "string"){
if(!pattern5.test(data94)){
const err135 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err135];
}
else {
vErrors.push(err135);
}
errors++;
}
}
var valid53 = _errs183 === errors;
}
else {
var valid53 = true;
}
}
}
var _valid9 = _errs181 === errors;
if(_valid9 && valid51){
valid51 = false;
passing9 = [passing9, 1];
}
else {
if(_valid9){
valid51 = true;
passing9 = 1;
if(props9 !== true){
props9 = props9 || {};
props9.algorithm = true;
props9.hex = true;
}
}
}
if(!valid51){
const err136 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing9},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err136];
}
else {
vErrors.push(err136);
}
errors++;
}
else {
errors = _errs177;
if(vErrors !== null){
if(_errs177){
vErrors.length = _errs177;
}
else {
vErrors = null;
}
}
}
if(errors === _errs175){
if(data90 && typeof data90 == "object" && !Array.isArray(data90)){
let missing17;
if(((data90.algorithm === undefined) && (missing17 = "algorithm")) || ((data90.hex === undefined) && (missing17 = "hex"))){
const err137 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing17},message:"must have required property '"+missing17+"'"};
if(vErrors === null){
vErrors = [err137];
}
else {
vErrors.push(err137);
}
errors++;
}
else {
const _errs184 = errors;
for(const key17 in data90){
if(!((key17 === "algorithm") || (key17 === "hex"))){
const err138 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key17},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err138];
}
else {
vErrors.push(err138);
}
errors++;
break;
}
}
if(_errs184 === errors){
if(data90.algorithm !== undefined){
let data95 = data90.algorithm;
const _errs185 = errors;
if(!((data95 === "sha1") || (data95 === "sha256"))){
const err139 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err139];
}
else {
vErrors.push(err139);
}
errors++;
}
var valid54 = _errs185 === errors;
}
else {
var valid54 = true;
}
if(valid54){
if(data90.hex !== undefined){
const _errs186 = errors;
if(typeof data90.hex !== "string"){
const err140 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err140];
}
else {
vErrors.push(err140);
}
errors++;
}
var valid54 = _errs186 === errors;
}
else {
var valid54 = true;
}
}
}
}
}
else {
const err141 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err141];
}
else {
vErrors.push(err141);
}
errors++;
}
}
var valid49 = _errs174 === errors;
}
else {
var valid49 = true;
}
if(valid49){
if(data.operation !== undefined){
const _errs188 = errors;
if("read_request" !== data.operation){
const err142 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/8/properties/operation/const",keyword:"const",params:{allowedValue: "read_request"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err142];
}
else {
vErrors.push(err142);
}
errors++;
}
var valid49 = _errs188 === errors;
}
else {
var valid49 = true;
}
if(valid49){
if(data.data !== undefined){
let data98 = data.data;
const _errs189 = errors;
if(errors === _errs189){
if(data98 && typeof data98 == "object" && !Array.isArray(data98)){
let missing18;
if((data98.record === undefined) && (missing18 = "record")){
const err143 = {instancePath:instancePath+"/data",schemaPath:"#/oneOf/8/properties/data/required",keyword:"required",params:{missingProperty: missing18},message:"must have required property '"+missing18+"'"};
if(vErrors === null){
vErrors = [err143];
}
else {
vErrors.push(err143);
}
errors++;
}
else {
const _errs191 = errors;
for(const key18 in data98){
if(!(key18 === "record")){
const err144 = {instancePath:instancePath+"/data",schemaPath:"#/oneOf/8/properties/data/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key18},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err144];
}
else {
vErrors.push(err144);
}
errors++;
break;
}
}
if(_errs191 === errors){
if(data98.record !== undefined){
if(!(validate60(data98.record, {instancePath:instancePath+"/data/record",parentData:data98,parentDataProperty:"record",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate60.errors : vErrors.concat(validate60.errors);
errors = vErrors.length;
}
}
}
}
}
else {
const err145 = {instancePath:instancePath+"/data",schemaPath:"#/oneOf/8/properties/data/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err145];
}
else {
vErrors.push(err145);
}
errors++;
}
}
var valid49 = _errs189 === errors;
}
else {
var valid49 = true;
}
}
}
}
}
}
}
}
else {
const err146 = {instancePath,schemaPath:"#/oneOf/8/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err146];
}
else {
vErrors.push(err146);
}
errors++;
}
}
var _valid0 = _errs169 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 8];
}
else {
if(_valid0){
valid0 = true;
passing0 = 8;
if(props1 !== true){
props1 = true;
}
}
const _errs193 = errors;
if(errors === _errs193){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing19;
if((((((data.formatVersion === undefined) && (missing19 = "formatVersion")) || ((data.kind === undefined) && (missing19 = "kind"))) || ((data.basis === undefined) && (missing19 = "basis"))) || ((data.operation === undefined) && (missing19 = "operation"))) || ((data.data === undefined) && (missing19 = "data"))){
const err147 = {instancePath,schemaPath:"#/oneOf/9/required",keyword:"required",params:{missingProperty: missing19},message:"must have required property '"+missing19+"'"};
if(vErrors === null){
vErrors = [err147];
}
else {
vErrors.push(err147);
}
errors++;
}
else {
const _errs195 = errors;
for(const key19 in data){
if(!(((((key19 === "formatVersion") || (key19 === "kind")) || (key19 === "basis")) || (key19 === "operation")) || (key19 === "data"))){
const err148 = {instancePath,schemaPath:"#/oneOf/9/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key19},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err148];
}
else {
vErrors.push(err148);
}
errors++;
break;
}
}
if(_errs195 === errors){
if(data.formatVersion !== undefined){
const _errs196 = errors;
if(1 !== data.formatVersion){
const err149 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/9/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err149];
}
else {
vErrors.push(err149);
}
errors++;
}
var valid56 = _errs196 === errors;
}
else {
var valid56 = true;
}
if(valid56){
if(data.kind !== undefined){
const _errs197 = errors;
if("read" !== data.kind){
const err150 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/9/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err150];
}
else {
vErrors.push(err150);
}
errors++;
}
var valid56 = _errs197 === errors;
}
else {
var valid56 = true;
}
if(valid56){
if(data.basis !== undefined){
let data102 = data.basis;
const _errs198 = errors;
const _errs199 = errors;
const _errs201 = errors;
let valid58 = false;
let passing10 = null;
const _errs202 = errors;
if(data102 && typeof data102 == "object" && !Array.isArray(data102)){
if(data102.algorithm !== undefined){
const _errs203 = errors;
if("sha1" !== data102.algorithm){
const err151 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err151];
}
else {
vErrors.push(err151);
}
errors++;
}
var valid59 = _errs203 === errors;
}
else {
var valid59 = true;
}
if(valid59){
if(data102.hex !== undefined){
let data104 = data102.hex;
const _errs204 = errors;
if(typeof data104 === "string"){
if(!pattern4.test(data104)){
const err152 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err152];
}
else {
vErrors.push(err152);
}
errors++;
}
}
var valid59 = _errs204 === errors;
}
else {
var valid59 = true;
}
}
}
var _valid10 = _errs202 === errors;
if(_valid10){
valid58 = true;
passing10 = 0;
var props10 = {};
props10.algorithm = true;
props10.hex = true;
}
const _errs205 = errors;
if(data102 && typeof data102 == "object" && !Array.isArray(data102)){
if(data102.algorithm !== undefined){
const _errs206 = errors;
if("sha256" !== data102.algorithm){
const err153 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err153];
}
else {
vErrors.push(err153);
}
errors++;
}
var valid60 = _errs206 === errors;
}
else {
var valid60 = true;
}
if(valid60){
if(data102.hex !== undefined){
let data106 = data102.hex;
const _errs207 = errors;
if(typeof data106 === "string"){
if(!pattern5.test(data106)){
const err154 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err154];
}
else {
vErrors.push(err154);
}
errors++;
}
}
var valid60 = _errs207 === errors;
}
else {
var valid60 = true;
}
}
}
var _valid10 = _errs205 === errors;
if(_valid10 && valid58){
valid58 = false;
passing10 = [passing10, 1];
}
else {
if(_valid10){
valid58 = true;
passing10 = 1;
if(props10 !== true){
props10 = props10 || {};
props10.algorithm = true;
props10.hex = true;
}
}
}
if(!valid58){
const err155 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing10},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err155];
}
else {
vErrors.push(err155);
}
errors++;
}
else {
errors = _errs201;
if(vErrors !== null){
if(_errs201){
vErrors.length = _errs201;
}
else {
vErrors = null;
}
}
}
if(errors === _errs199){
if(data102 && typeof data102 == "object" && !Array.isArray(data102)){
let missing20;
if(((data102.algorithm === undefined) && (missing20 = "algorithm")) || ((data102.hex === undefined) && (missing20 = "hex"))){
const err156 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing20},message:"must have required property '"+missing20+"'"};
if(vErrors === null){
vErrors = [err156];
}
else {
vErrors.push(err156);
}
errors++;
}
else {
const _errs208 = errors;
for(const key20 in data102){
if(!((key20 === "algorithm") || (key20 === "hex"))){
const err157 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key20},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err157];
}
else {
vErrors.push(err157);
}
errors++;
break;
}
}
if(_errs208 === errors){
if(data102.algorithm !== undefined){
let data107 = data102.algorithm;
const _errs209 = errors;
if(!((data107 === "sha1") || (data107 === "sha256"))){
const err158 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err158];
}
else {
vErrors.push(err158);
}
errors++;
}
var valid61 = _errs209 === errors;
}
else {
var valid61 = true;
}
if(valid61){
if(data102.hex !== undefined){
const _errs210 = errors;
if(typeof data102.hex !== "string"){
const err159 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err159];
}
else {
vErrors.push(err159);
}
errors++;
}
var valid61 = _errs210 === errors;
}
else {
var valid61 = true;
}
}
}
}
}
else {
const err160 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err160];
}
else {
vErrors.push(err160);
}
errors++;
}
}
var valid56 = _errs198 === errors;
}
else {
var valid56 = true;
}
if(valid56){
if(data.operation !== undefined){
const _errs212 = errors;
if("read_resolution" !== data.operation){
const err161 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/9/properties/operation/const",keyword:"const",params:{allowedValue: "read_resolution"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err161];
}
else {
vErrors.push(err161);
}
errors++;
}
var valid56 = _errs212 === errors;
}
else {
var valid56 = true;
}
if(valid56){
if(data.data !== undefined){
let data110 = data.data;
const _errs213 = errors;
if(errors === _errs213){
if(data110 && typeof data110 == "object" && !Array.isArray(data110)){
let missing21;
if((data110.record === undefined) && (missing21 = "record")){
const err162 = {instancePath:instancePath+"/data",schemaPath:"#/oneOf/9/properties/data/required",keyword:"required",params:{missingProperty: missing21},message:"must have required property '"+missing21+"'"};
if(vErrors === null){
vErrors = [err162];
}
else {
vErrors.push(err162);
}
errors++;
}
else {
const _errs215 = errors;
for(const key21 in data110){
if(!(key21 === "record")){
const err163 = {instancePath:instancePath+"/data",schemaPath:"#/oneOf/9/properties/data/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key21},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err163];
}
else {
vErrors.push(err163);
}
errors++;
break;
}
}
if(_errs215 === errors){
if(data110.record !== undefined){
if(!(validate60(data110.record, {instancePath:instancePath+"/data/record",parentData:data110,parentDataProperty:"record",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate60.errors : vErrors.concat(validate60.errors);
errors = vErrors.length;
}
}
}
}
}
else {
const err164 = {instancePath:instancePath+"/data",schemaPath:"#/oneOf/9/properties/data/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err164];
}
else {
vErrors.push(err164);
}
errors++;
}
}
var valid56 = _errs213 === errors;
}
else {
var valid56 = true;
}
}
}
}
}
}
}
}
else {
const err165 = {instancePath,schemaPath:"#/oneOf/9/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err165];
}
else {
vErrors.push(err165);
}
errors++;
}
}
var _valid0 = _errs193 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 9];
}
else {
if(_valid0){
valid0 = true;
passing0 = 9;
if(props1 !== true){
props1 = true;
}
}
const _errs217 = errors;
if(errors === _errs217){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing22;
if((((((data.formatVersion === undefined) && (missing22 = "formatVersion")) || ((data.kind === undefined) && (missing22 = "kind"))) || ((data.basis === undefined) && (missing22 = "basis"))) || ((data.operation === undefined) && (missing22 = "operation"))) || ((data.data === undefined) && (missing22 = "data"))){
const err166 = {instancePath,schemaPath:"#/oneOf/10/required",keyword:"required",params:{missingProperty: missing22},message:"must have required property '"+missing22+"'"};
if(vErrors === null){
vErrors = [err166];
}
else {
vErrors.push(err166);
}
errors++;
}
else {
const _errs219 = errors;
for(const key22 in data){
if(!(((((key22 === "formatVersion") || (key22 === "kind")) || (key22 === "basis")) || (key22 === "operation")) || (key22 === "data"))){
const err167 = {instancePath,schemaPath:"#/oneOf/10/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key22},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err167];
}
else {
vErrors.push(err167);
}
errors++;
break;
}
}
if(_errs219 === errors){
if(data.formatVersion !== undefined){
const _errs220 = errors;
if(1 !== data.formatVersion){
const err168 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/10/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err168];
}
else {
vErrors.push(err168);
}
errors++;
}
var valid63 = _errs220 === errors;
}
else {
var valid63 = true;
}
if(valid63){
if(data.kind !== undefined){
const _errs221 = errors;
if("read" !== data.kind){
const err169 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/10/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err169];
}
else {
vErrors.push(err169);
}
errors++;
}
var valid63 = _errs221 === errors;
}
else {
var valid63 = true;
}
if(valid63){
if(data.basis !== undefined){
let data114 = data.basis;
const _errs222 = errors;
const _errs223 = errors;
const _errs225 = errors;
let valid65 = false;
let passing11 = null;
const _errs226 = errors;
if(data114 && typeof data114 == "object" && !Array.isArray(data114)){
if(data114.algorithm !== undefined){
const _errs227 = errors;
if("sha1" !== data114.algorithm){
const err170 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err170];
}
else {
vErrors.push(err170);
}
errors++;
}
var valid66 = _errs227 === errors;
}
else {
var valid66 = true;
}
if(valid66){
if(data114.hex !== undefined){
let data116 = data114.hex;
const _errs228 = errors;
if(typeof data116 === "string"){
if(!pattern4.test(data116)){
const err171 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err171];
}
else {
vErrors.push(err171);
}
errors++;
}
}
var valid66 = _errs228 === errors;
}
else {
var valid66 = true;
}
}
}
var _valid11 = _errs226 === errors;
if(_valid11){
valid65 = true;
passing11 = 0;
var props11 = {};
props11.algorithm = true;
props11.hex = true;
}
const _errs229 = errors;
if(data114 && typeof data114 == "object" && !Array.isArray(data114)){
if(data114.algorithm !== undefined){
const _errs230 = errors;
if("sha256" !== data114.algorithm){
const err172 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err172];
}
else {
vErrors.push(err172);
}
errors++;
}
var valid67 = _errs230 === errors;
}
else {
var valid67 = true;
}
if(valid67){
if(data114.hex !== undefined){
let data118 = data114.hex;
const _errs231 = errors;
if(typeof data118 === "string"){
if(!pattern5.test(data118)){
const err173 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err173];
}
else {
vErrors.push(err173);
}
errors++;
}
}
var valid67 = _errs231 === errors;
}
else {
var valid67 = true;
}
}
}
var _valid11 = _errs229 === errors;
if(_valid11 && valid65){
valid65 = false;
passing11 = [passing11, 1];
}
else {
if(_valid11){
valid65 = true;
passing11 = 1;
if(props11 !== true){
props11 = props11 || {};
props11.algorithm = true;
props11.hex = true;
}
}
}
if(!valid65){
const err174 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing11},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err174];
}
else {
vErrors.push(err174);
}
errors++;
}
else {
errors = _errs225;
if(vErrors !== null){
if(_errs225){
vErrors.length = _errs225;
}
else {
vErrors = null;
}
}
}
if(errors === _errs223){
if(data114 && typeof data114 == "object" && !Array.isArray(data114)){
let missing23;
if(((data114.algorithm === undefined) && (missing23 = "algorithm")) || ((data114.hex === undefined) && (missing23 = "hex"))){
const err175 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing23},message:"must have required property '"+missing23+"'"};
if(vErrors === null){
vErrors = [err175];
}
else {
vErrors.push(err175);
}
errors++;
}
else {
const _errs232 = errors;
for(const key23 in data114){
if(!((key23 === "algorithm") || (key23 === "hex"))){
const err176 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key23},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err176];
}
else {
vErrors.push(err176);
}
errors++;
break;
}
}
if(_errs232 === errors){
if(data114.algorithm !== undefined){
let data119 = data114.algorithm;
const _errs233 = errors;
if(!((data119 === "sha1") || (data119 === "sha256"))){
const err177 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err177];
}
else {
vErrors.push(err177);
}
errors++;
}
var valid68 = _errs233 === errors;
}
else {
var valid68 = true;
}
if(valid68){
if(data114.hex !== undefined){
const _errs234 = errors;
if(typeof data114.hex !== "string"){
const err178 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err178];
}
else {
vErrors.push(err178);
}
errors++;
}
var valid68 = _errs234 === errors;
}
else {
var valid68 = true;
}
}
}
}
}
else {
const err179 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err179];
}
else {
vErrors.push(err179);
}
errors++;
}
}
var valid63 = _errs222 === errors;
}
else {
var valid63 = true;
}
if(valid63){
if(data.operation !== undefined){
const _errs236 = errors;
if("read_manifest" !== data.operation){
const err180 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/10/properties/operation/const",keyword:"const",params:{allowedValue: "read_manifest"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err180];
}
else {
vErrors.push(err180);
}
errors++;
}
var valid63 = _errs236 === errors;
}
else {
var valid63 = true;
}
if(valid63){
if(data.data !== undefined){
let data122 = data.data;
const _errs237 = errors;
if(errors === _errs237){
if(data122 && typeof data122 == "object" && !Array.isArray(data122)){
let missing24;
if((data122.record === undefined) && (missing24 = "record")){
const err181 = {instancePath:instancePath+"/data",schemaPath:"#/oneOf/10/properties/data/required",keyword:"required",params:{missingProperty: missing24},message:"must have required property '"+missing24+"'"};
if(vErrors === null){
vErrors = [err181];
}
else {
vErrors.push(err181);
}
errors++;
}
else {
const _errs239 = errors;
for(const key24 in data122){
if(!(key24 === "record")){
const err182 = {instancePath:instancePath+"/data",schemaPath:"#/oneOf/10/properties/data/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key24},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err182];
}
else {
vErrors.push(err182);
}
errors++;
break;
}
}
if(_errs239 === errors){
if(data122.record !== undefined){
if(!(validate60(data122.record, {instancePath:instancePath+"/data/record",parentData:data122,parentDataProperty:"record",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate60.errors : vErrors.concat(validate60.errors);
errors = vErrors.length;
}
}
}
}
}
else {
const err183 = {instancePath:instancePath+"/data",schemaPath:"#/oneOf/10/properties/data/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err183];
}
else {
vErrors.push(err183);
}
errors++;
}
}
var valid63 = _errs237 === errors;
}
else {
var valid63 = true;
}
}
}
}
}
}
}
}
else {
const err184 = {instancePath,schemaPath:"#/oneOf/10/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err184];
}
else {
vErrors.push(err184);
}
errors++;
}
}
var _valid0 = _errs217 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 10];
}
else {
if(_valid0){
valid0 = true;
passing0 = 10;
if(props1 !== true){
props1 = true;
}
}
const _errs241 = errors;
if(errors === _errs241){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing25;
if((((((data.formatVersion === undefined) && (missing25 = "formatVersion")) || ((data.kind === undefined) && (missing25 = "kind"))) || ((data.basis === undefined) && (missing25 = "basis"))) || ((data.operation === undefined) && (missing25 = "operation"))) || ((data.data === undefined) && (missing25 = "data"))){
const err185 = {instancePath,schemaPath:"#/oneOf/11/required",keyword:"required",params:{missingProperty: missing25},message:"must have required property '"+missing25+"'"};
if(vErrors === null){
vErrors = [err185];
}
else {
vErrors.push(err185);
}
errors++;
}
else {
const _errs243 = errors;
for(const key25 in data){
if(!(((((key25 === "formatVersion") || (key25 === "kind")) || (key25 === "basis")) || (key25 === "operation")) || (key25 === "data"))){
const err186 = {instancePath,schemaPath:"#/oneOf/11/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key25},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err186];
}
else {
vErrors.push(err186);
}
errors++;
break;
}
}
if(_errs243 === errors){
if(data.formatVersion !== undefined){
const _errs244 = errors;
if(1 !== data.formatVersion){
const err187 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/11/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err187];
}
else {
vErrors.push(err187);
}
errors++;
}
var valid70 = _errs244 === errors;
}
else {
var valid70 = true;
}
if(valid70){
if(data.kind !== undefined){
const _errs245 = errors;
if("read" !== data.kind){
const err188 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/11/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err188];
}
else {
vErrors.push(err188);
}
errors++;
}
var valid70 = _errs245 === errors;
}
else {
var valid70 = true;
}
if(valid70){
if(data.basis !== undefined){
let data126 = data.basis;
const _errs246 = errors;
const _errs247 = errors;
const _errs249 = errors;
let valid72 = false;
let passing12 = null;
const _errs250 = errors;
if(data126 && typeof data126 == "object" && !Array.isArray(data126)){
if(data126.algorithm !== undefined){
const _errs251 = errors;
if("sha1" !== data126.algorithm){
const err189 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err189];
}
else {
vErrors.push(err189);
}
errors++;
}
var valid73 = _errs251 === errors;
}
else {
var valid73 = true;
}
if(valid73){
if(data126.hex !== undefined){
let data128 = data126.hex;
const _errs252 = errors;
if(typeof data128 === "string"){
if(!pattern4.test(data128)){
const err190 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err190];
}
else {
vErrors.push(err190);
}
errors++;
}
}
var valid73 = _errs252 === errors;
}
else {
var valid73 = true;
}
}
}
var _valid12 = _errs250 === errors;
if(_valid12){
valid72 = true;
passing12 = 0;
var props12 = {};
props12.algorithm = true;
props12.hex = true;
}
const _errs253 = errors;
if(data126 && typeof data126 == "object" && !Array.isArray(data126)){
if(data126.algorithm !== undefined){
const _errs254 = errors;
if("sha256" !== data126.algorithm){
const err191 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err191];
}
else {
vErrors.push(err191);
}
errors++;
}
var valid74 = _errs254 === errors;
}
else {
var valid74 = true;
}
if(valid74){
if(data126.hex !== undefined){
let data130 = data126.hex;
const _errs255 = errors;
if(typeof data130 === "string"){
if(!pattern5.test(data130)){
const err192 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err192];
}
else {
vErrors.push(err192);
}
errors++;
}
}
var valid74 = _errs255 === errors;
}
else {
var valid74 = true;
}
}
}
var _valid12 = _errs253 === errors;
if(_valid12 && valid72){
valid72 = false;
passing12 = [passing12, 1];
}
else {
if(_valid12){
valid72 = true;
passing12 = 1;
if(props12 !== true){
props12 = props12 || {};
props12.algorithm = true;
props12.hex = true;
}
}
}
if(!valid72){
const err193 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing12},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err193];
}
else {
vErrors.push(err193);
}
errors++;
}
else {
errors = _errs249;
if(vErrors !== null){
if(_errs249){
vErrors.length = _errs249;
}
else {
vErrors = null;
}
}
}
if(errors === _errs247){
if(data126 && typeof data126 == "object" && !Array.isArray(data126)){
let missing26;
if(((data126.algorithm === undefined) && (missing26 = "algorithm")) || ((data126.hex === undefined) && (missing26 = "hex"))){
const err194 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing26},message:"must have required property '"+missing26+"'"};
if(vErrors === null){
vErrors = [err194];
}
else {
vErrors.push(err194);
}
errors++;
}
else {
const _errs256 = errors;
for(const key26 in data126){
if(!((key26 === "algorithm") || (key26 === "hex"))){
const err195 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key26},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err195];
}
else {
vErrors.push(err195);
}
errors++;
break;
}
}
if(_errs256 === errors){
if(data126.algorithm !== undefined){
let data131 = data126.algorithm;
const _errs257 = errors;
if(!((data131 === "sha1") || (data131 === "sha256"))){
const err196 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err196];
}
else {
vErrors.push(err196);
}
errors++;
}
var valid75 = _errs257 === errors;
}
else {
var valid75 = true;
}
if(valid75){
if(data126.hex !== undefined){
const _errs258 = errors;
if(typeof data126.hex !== "string"){
const err197 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err197];
}
else {
vErrors.push(err197);
}
errors++;
}
var valid75 = _errs258 === errors;
}
else {
var valid75 = true;
}
}
}
}
}
else {
const err198 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err198];
}
else {
vErrors.push(err198);
}
errors++;
}
}
var valid70 = _errs246 === errors;
}
else {
var valid70 = true;
}
if(valid70){
if(data.operation !== undefined){
const _errs260 = errors;
if("read_approval_trace" !== data.operation){
const err199 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/11/properties/operation/const",keyword:"const",params:{allowedValue: "read_approval_trace"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err199];
}
else {
vErrors.push(err199);
}
errors++;
}
var valid70 = _errs260 === errors;
}
else {
var valid70 = true;
}
if(valid70){
if(data.data !== undefined){
const _errs261 = errors;
if(!(validate45(data.data, {instancePath:instancePath+"/data",parentData:data,parentDataProperty:"data",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate45.errors : vErrors.concat(validate45.errors);
errors = vErrors.length;
}
var valid70 = _errs261 === errors;
}
else {
var valid70 = true;
}
}
}
}
}
}
}
}
else {
const err200 = {instancePath,schemaPath:"#/oneOf/11/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err200];
}
else {
vErrors.push(err200);
}
errors++;
}
}
var _valid0 = _errs241 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 11];
}
else {
if(_valid0){
valid0 = true;
passing0 = 11;
if(props1 !== true){
props1 = true;
}
}
const _errs262 = errors;
if(errors === _errs262){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing27;
if((((((data.formatVersion === undefined) && (missing27 = "formatVersion")) || ((data.kind === undefined) && (missing27 = "kind"))) || ((data.basis === undefined) && (missing27 = "basis"))) || ((data.operation === undefined) && (missing27 = "operation"))) || ((data.data === undefined) && (missing27 = "data"))){
const err201 = {instancePath,schemaPath:"#/oneOf/12/required",keyword:"required",params:{missingProperty: missing27},message:"must have required property '"+missing27+"'"};
if(vErrors === null){
vErrors = [err201];
}
else {
vErrors.push(err201);
}
errors++;
}
else {
const _errs264 = errors;
for(const key27 in data){
if(!(((((key27 === "formatVersion") || (key27 === "kind")) || (key27 === "basis")) || (key27 === "operation")) || (key27 === "data"))){
const err202 = {instancePath,schemaPath:"#/oneOf/12/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key27},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err202];
}
else {
vErrors.push(err202);
}
errors++;
break;
}
}
if(_errs264 === errors){
if(data.formatVersion !== undefined){
const _errs265 = errors;
if(1 !== data.formatVersion){
const err203 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/12/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err203];
}
else {
vErrors.push(err203);
}
errors++;
}
var valid76 = _errs265 === errors;
}
else {
var valid76 = true;
}
if(valid76){
if(data.kind !== undefined){
const _errs266 = errors;
if("read" !== data.kind){
const err204 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/12/properties/kind/const",keyword:"const",params:{allowedValue: "read"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err204];
}
else {
vErrors.push(err204);
}
errors++;
}
var valid76 = _errs266 === errors;
}
else {
var valid76 = true;
}
if(valid76){
if(data.basis !== undefined){
let data137 = data.basis;
const _errs267 = errors;
const _errs268 = errors;
const _errs270 = errors;
let valid78 = false;
let passing13 = null;
const _errs271 = errors;
if(data137 && typeof data137 == "object" && !Array.isArray(data137)){
if(data137.algorithm !== undefined){
const _errs272 = errors;
if("sha1" !== data137.algorithm){
const err205 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err205];
}
else {
vErrors.push(err205);
}
errors++;
}
var valid79 = _errs272 === errors;
}
else {
var valid79 = true;
}
if(valid79){
if(data137.hex !== undefined){
let data139 = data137.hex;
const _errs273 = errors;
if(typeof data139 === "string"){
if(!pattern4.test(data139)){
const err206 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err206];
}
else {
vErrors.push(err206);
}
errors++;
}
}
var valid79 = _errs273 === errors;
}
else {
var valid79 = true;
}
}
}
var _valid13 = _errs271 === errors;
if(_valid13){
valid78 = true;
passing13 = 0;
var props13 = {};
props13.algorithm = true;
props13.hex = true;
}
const _errs274 = errors;
if(data137 && typeof data137 == "object" && !Array.isArray(data137)){
if(data137.algorithm !== undefined){
const _errs275 = errors;
if("sha256" !== data137.algorithm){
const err207 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err207];
}
else {
vErrors.push(err207);
}
errors++;
}
var valid80 = _errs275 === errors;
}
else {
var valid80 = true;
}
if(valid80){
if(data137.hex !== undefined){
let data141 = data137.hex;
const _errs276 = errors;
if(typeof data141 === "string"){
if(!pattern5.test(data141)){
const err208 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err208];
}
else {
vErrors.push(err208);
}
errors++;
}
}
var valid80 = _errs276 === errors;
}
else {
var valid80 = true;
}
}
}
var _valid13 = _errs274 === errors;
if(_valid13 && valid78){
valid78 = false;
passing13 = [passing13, 1];
}
else {
if(_valid13){
valid78 = true;
passing13 = 1;
if(props13 !== true){
props13 = props13 || {};
props13.algorithm = true;
props13.hex = true;
}
}
}
if(!valid78){
const err209 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing13},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err209];
}
else {
vErrors.push(err209);
}
errors++;
}
else {
errors = _errs270;
if(vErrors !== null){
if(_errs270){
vErrors.length = _errs270;
}
else {
vErrors = null;
}
}
}
if(errors === _errs268){
if(data137 && typeof data137 == "object" && !Array.isArray(data137)){
let missing28;
if(((data137.algorithm === undefined) && (missing28 = "algorithm")) || ((data137.hex === undefined) && (missing28 = "hex"))){
const err210 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing28},message:"must have required property '"+missing28+"'"};
if(vErrors === null){
vErrors = [err210];
}
else {
vErrors.push(err210);
}
errors++;
}
else {
const _errs277 = errors;
for(const key28 in data137){
if(!((key28 === "algorithm") || (key28 === "hex"))){
const err211 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key28},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err211];
}
else {
vErrors.push(err211);
}
errors++;
break;
}
}
if(_errs277 === errors){
if(data137.algorithm !== undefined){
let data142 = data137.algorithm;
const _errs278 = errors;
if(!((data142 === "sha1") || (data142 === "sha256"))){
const err212 = {instancePath:instancePath+"/basis/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err212];
}
else {
vErrors.push(err212);
}
errors++;
}
var valid81 = _errs278 === errors;
}
else {
var valid81 = true;
}
if(valid81){
if(data137.hex !== undefined){
const _errs279 = errors;
if(typeof data137.hex !== "string"){
const err213 = {instancePath:instancePath+"/basis/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err213];
}
else {
vErrors.push(err213);
}
errors++;
}
var valid81 = _errs279 === errors;
}
else {
var valid81 = true;
}
}
}
}
}
else {
const err214 = {instancePath:instancePath+"/basis",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err214];
}
else {
vErrors.push(err214);
}
errors++;
}
}
var valid76 = _errs267 === errors;
}
else {
var valid76 = true;
}
if(valid76){
if(data.operation !== undefined){
const _errs281 = errors;
if("read_review_bundle" !== data.operation){
const err215 = {instancePath:instancePath+"/operation",schemaPath:"#/oneOf/12/properties/operation/const",keyword:"const",params:{allowedValue: "read_review_bundle"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err215];
}
else {
vErrors.push(err215);
}
errors++;
}
var valid76 = _errs281 === errors;
}
else {
var valid76 = true;
}
if(valid76){
if(data.data !== undefined){
const _errs282 = errors;
if(!(validate138(data.data, {instancePath:instancePath+"/data",parentData:data,parentDataProperty:"data",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate138.errors : vErrors.concat(validate138.errors);
errors = vErrors.length;
}
var valid76 = _errs282 === errors;
}
else {
var valid76 = true;
}
}
}
}
}
}
}
}
else {
const err216 = {instancePath,schemaPath:"#/oneOf/12/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err216];
}
else {
vErrors.push(err216);
}
errors++;
}
}
var _valid0 = _errs262 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 12];
}
else {
if(_valid0){
valid0 = true;
passing0 = 12;
if(props1 !== true){
props1 = true;
}
}
const _errs283 = errors;
if(errors === _errs283){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing29;
if((((((((((data.formatVersion === undefined) && (missing29 = "formatVersion")) || ((data.kind === undefined) && (missing29 = "kind"))) || ((data.identity === undefined) && (missing29 = "identity"))) || ((data.fingerprint === undefined) && (missing29 = "fingerprint"))) || ((data.validatedAt === undefined) && (missing29 = "validatedAt"))) || ((data.authenticatedByCredentialId === undefined) && (missing29 = "authenticatedByCredentialId"))) || ((data.outcome === undefined) && (missing29 = "outcome"))) || ((data.commandReplayed === undefined) && (missing29 = "commandReplayed"))) || ((data.publication === undefined) && (missing29 = "publication"))){
const err217 = {instancePath,schemaPath:"#/oneOf/13/required",keyword:"required",params:{missingProperty: missing29},message:"must have required property '"+missing29+"'"};
if(vErrors === null){
vErrors = [err217];
}
else {
vErrors.push(err217);
}
errors++;
}
else {
const _errs285 = errors;
for(const key29 in data){
if(!(func4.call(schema32.oneOf[13].properties, key29))){
const err218 = {instancePath,schemaPath:"#/oneOf/13/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key29},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err218];
}
else {
vErrors.push(err218);
}
errors++;
break;
}
}
if(_errs285 === errors){
if(data.formatVersion !== undefined){
const _errs286 = errors;
if(1 !== data.formatVersion){
const err219 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/13/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err219];
}
else {
vErrors.push(err219);
}
errors++;
}
var valid82 = _errs286 === errors;
}
else {
var valid82 = true;
}
if(valid82){
if(data.kind !== undefined){
const _errs287 = errors;
if("settled" !== data.kind){
const err220 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/13/properties/kind/const",keyword:"const",params:{allowedValue: "settled"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err220];
}
else {
vErrors.push(err220);
}
errors++;
}
var valid82 = _errs287 === errors;
}
else {
var valid82 = true;
}
if(valid82){
if(data.identity !== undefined){
const _errs288 = errors;
if(!(validate143(data.identity, {instancePath:instancePath+"/identity",parentData:data,parentDataProperty:"identity",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate143.errors : vErrors.concat(validate143.errors);
errors = vErrors.length;
}
var valid82 = _errs288 === errors;
}
else {
var valid82 = true;
}
if(valid82){
if(data.fingerprint !== undefined){
let data149 = data.fingerprint;
const _errs289 = errors;
const _errs290 = errors;
if(errors === _errs290){
if(typeof data149 === "string"){
if(!pattern5.test(data149)){
const err221 = {instancePath:instancePath+"/fingerprint",schemaPath:"#/$defs/digest/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err221];
}
else {
vErrors.push(err221);
}
errors++;
}
}
else {
const err222 = {instancePath:instancePath+"/fingerprint",schemaPath:"#/$defs/digest/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err222];
}
else {
vErrors.push(err222);
}
errors++;
}
}
var valid82 = _errs289 === errors;
}
else {
var valid82 = true;
}
if(valid82){
if(data.validatedAt !== undefined){
let data150 = data.validatedAt;
const _errs292 = errors;
const _errs293 = errors;
const _errs295 = errors;
let valid85 = false;
let passing14 = null;
const _errs296 = errors;
if(data150 && typeof data150 == "object" && !Array.isArray(data150)){
if(data150.algorithm !== undefined){
const _errs297 = errors;
if("sha1" !== data150.algorithm){
const err223 = {instancePath:instancePath+"/validatedAt/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err223];
}
else {
vErrors.push(err223);
}
errors++;
}
var valid86 = _errs297 === errors;
}
else {
var valid86 = true;
}
if(valid86){
if(data150.hex !== undefined){
let data152 = data150.hex;
const _errs298 = errors;
if(typeof data152 === "string"){
if(!pattern4.test(data152)){
const err224 = {instancePath:instancePath+"/validatedAt/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err224];
}
else {
vErrors.push(err224);
}
errors++;
}
}
var valid86 = _errs298 === errors;
}
else {
var valid86 = true;
}
}
}
var _valid14 = _errs296 === errors;
if(_valid14){
valid85 = true;
passing14 = 0;
var props14 = {};
props14.algorithm = true;
props14.hex = true;
}
const _errs299 = errors;
if(data150 && typeof data150 == "object" && !Array.isArray(data150)){
if(data150.algorithm !== undefined){
const _errs300 = errors;
if("sha256" !== data150.algorithm){
const err225 = {instancePath:instancePath+"/validatedAt/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err225];
}
else {
vErrors.push(err225);
}
errors++;
}
var valid87 = _errs300 === errors;
}
else {
var valid87 = true;
}
if(valid87){
if(data150.hex !== undefined){
let data154 = data150.hex;
const _errs301 = errors;
if(typeof data154 === "string"){
if(!pattern5.test(data154)){
const err226 = {instancePath:instancePath+"/validatedAt/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err226];
}
else {
vErrors.push(err226);
}
errors++;
}
}
var valid87 = _errs301 === errors;
}
else {
var valid87 = true;
}
}
}
var _valid14 = _errs299 === errors;
if(_valid14 && valid85){
valid85 = false;
passing14 = [passing14, 1];
}
else {
if(_valid14){
valid85 = true;
passing14 = 1;
if(props14 !== true){
props14 = props14 || {};
props14.algorithm = true;
props14.hex = true;
}
}
}
if(!valid85){
const err227 = {instancePath:instancePath+"/validatedAt",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing14},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err227];
}
else {
vErrors.push(err227);
}
errors++;
}
else {
errors = _errs295;
if(vErrors !== null){
if(_errs295){
vErrors.length = _errs295;
}
else {
vErrors = null;
}
}
}
if(errors === _errs293){
if(data150 && typeof data150 == "object" && !Array.isArray(data150)){
let missing30;
if(((data150.algorithm === undefined) && (missing30 = "algorithm")) || ((data150.hex === undefined) && (missing30 = "hex"))){
const err228 = {instancePath:instancePath+"/validatedAt",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing30},message:"must have required property '"+missing30+"'"};
if(vErrors === null){
vErrors = [err228];
}
else {
vErrors.push(err228);
}
errors++;
}
else {
const _errs302 = errors;
for(const key30 in data150){
if(!((key30 === "algorithm") || (key30 === "hex"))){
const err229 = {instancePath:instancePath+"/validatedAt",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key30},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err229];
}
else {
vErrors.push(err229);
}
errors++;
break;
}
}
if(_errs302 === errors){
if(data150.algorithm !== undefined){
let data155 = data150.algorithm;
const _errs303 = errors;
if(!((data155 === "sha1") || (data155 === "sha256"))){
const err230 = {instancePath:instancePath+"/validatedAt/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err230];
}
else {
vErrors.push(err230);
}
errors++;
}
var valid88 = _errs303 === errors;
}
else {
var valid88 = true;
}
if(valid88){
if(data150.hex !== undefined){
const _errs304 = errors;
if(typeof data150.hex !== "string"){
const err231 = {instancePath:instancePath+"/validatedAt/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err231];
}
else {
vErrors.push(err231);
}
errors++;
}
var valid88 = _errs304 === errors;
}
else {
var valid88 = true;
}
}
}
}
}
else {
const err232 = {instancePath:instancePath+"/validatedAt",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err232];
}
else {
vErrors.push(err232);
}
errors++;
}
}
var valid82 = _errs292 === errors;
}
else {
var valid82 = true;
}
if(valid82){
if(data.authenticatedByCredentialId !== undefined){
let data157 = data.authenticatedByCredentialId;
const _errs306 = errors;
const _errs307 = errors;
if(errors === _errs307){
if(typeof data157 === "string"){
if(!pattern6.test(data157)){
const err233 = {instancePath:instancePath+"/authenticatedByCredentialId",schemaPath:"#/$defs/uuid/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err233];
}
else {
vErrors.push(err233);
}
errors++;
}
}
else {
const err234 = {instancePath:instancePath+"/authenticatedByCredentialId",schemaPath:"#/$defs/uuid/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err234];
}
else {
vErrors.push(err234);
}
errors++;
}
}
var valid82 = _errs306 === errors;
}
else {
var valid82 = true;
}
if(valid82){
if(data.outcome !== undefined){
let data158 = data.outcome;
const _errs309 = errors;
const _errs310 = errors;
let valid90 = false;
let passing15 = null;
const _errs311 = errors;
if(!(validate147(data158, {instancePath:instancePath+"/outcome",parentData:data,parentDataProperty:"outcome",rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate147.errors : vErrors.concat(validate147.errors);
errors = vErrors.length;
}
var _valid15 = _errs311 === errors;
if(_valid15){
valid90 = true;
passing15 = 0;
var props15 = true;
}
const _errs312 = errors;
const _errs313 = errors;
const _errs315 = errors;
let valid92 = false;
let passing16 = null;
const _errs316 = errors;
if(data158 && typeof data158 == "object" && !Array.isArray(data158)){
if(data158.domain !== undefined){
const _errs317 = errors;
if("core" !== data158.domain){
const err235 = {instancePath:instancePath+"/outcome/domain",schemaPath:"#/$defs/rejection/oneOf/0/properties/domain/const",keyword:"const",params:{allowedValue: "core"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err235];
}
else {
vErrors.push(err235);
}
errors++;
}
var valid93 = _errs317 === errors;
}
else {
var valid93 = true;
}
if(valid93){
if(data158.code !== undefined){
let data160 = data158.code;
const _errs318 = errors;
if(!(((((((((((((((((((data160 === "NOT_FOUND") || (data160 === "DUPLICATE_ID")) || (data160 === "INVALID_REFERENCE")) || (data160 === "FOREIGN_REFERENCE")) || (data160 === "MISSING_CONTEXT")) || (data160 === "STALE_SUBJECT")) || (data160 === "DIGEST_MISMATCH")) || (data160 === "ALREADY_RESOLVED")) || (data160 === "UNKNOWN_COMMENT_ITEM")) || (data160 === "INCOMPLETE_PACKET")) || (data160 === "UNAPPROVED_INPUT")) || (data160 === "BLOCKED_INPUT")) || (data160 === "INVALID_CONTINUITY")) || (data160 === "CONTEXT_CHANGED")) || (data160 === "INCOMPLETE_ASSESSMENT")) || (data160 === "CONFLICTING_SELECTION")) || (data160 === "UNSUPPORTED_CONTEXT_MIX")) || (data160 === "LAST_AUTHORITY")) || (data160 === "GENERATION_EXHAUSTED"))){
const err236 = {instancePath:instancePath+"/outcome/code",schemaPath:"#/$defs/rejection/oneOf/0/properties/code/enum",keyword:"enum",params:{allowedValues: schema160.oneOf[0].properties.code.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err236];
}
else {
vErrors.push(err236);
}
errors++;
}
var valid93 = _errs318 === errors;
}
else {
var valid93 = true;
}
}
}
var _valid16 = _errs316 === errors;
if(_valid16){
valid92 = true;
passing16 = 0;
var props16 = {};
props16.domain = true;
props16.code = true;
}
const _errs319 = errors;
if(data158 && typeof data158 == "object" && !Array.isArray(data158)){
if(data158.domain !== undefined){
const _errs320 = errors;
if("auth" !== data158.domain){
const err237 = {instancePath:instancePath+"/outcome/domain",schemaPath:"#/$defs/rejection/oneOf/1/properties/domain/const",keyword:"const",params:{allowedValue: "auth"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err237];
}
else {
vErrors.push(err237);
}
errors++;
}
var valid94 = _errs320 === errors;
}
else {
var valid94 = true;
}
if(valid94){
if(data158.code !== undefined){
let data162 = data158.code;
const _errs321 = errors;
if(!(((((((((data162 === "AUTH_TARGET_NOT_FOUND") || (data162 === "AUTH_STALE_CREDENTIAL")) || (data162 === "AUTH_STALE_GRANT")) || (data162 === "AUTH_ALREADY_REVOKED")) || (data162 === "AUTH_ALREADY_REPLACED")) || (data162 === "AUTH_DUPLICATE_CREDENTIAL")) || (data162 === "AUTH_OWNER_LOCKOUT")) || (data162 === "AUTH_FIXED_OWNER_POLICY")) || (data162 === "AUTH_GENERATION_EXHAUSTED"))){
const err238 = {instancePath:instancePath+"/outcome/code",schemaPath:"#/$defs/rejection/oneOf/1/properties/code/enum",keyword:"enum",params:{allowedValues: schema160.oneOf[1].properties.code.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err238];
}
else {
vErrors.push(err238);
}
errors++;
}
var valid94 = _errs321 === errors;
}
else {
var valid94 = true;
}
}
}
var _valid16 = _errs319 === errors;
if(_valid16 && valid92){
valid92 = false;
passing16 = [passing16, 1];
}
else {
if(_valid16){
valid92 = true;
passing16 = 1;
if(props16 !== true){
props16 = props16 || {};
props16.domain = true;
props16.code = true;
}
}
}
if(!valid92){
const err239 = {instancePath:instancePath+"/outcome",schemaPath:"#/$defs/rejection/oneOf",keyword:"oneOf",params:{passingSchemas: passing16},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err239];
}
else {
vErrors.push(err239);
}
errors++;
}
else {
errors = _errs315;
if(vErrors !== null){
if(_errs315){
vErrors.length = _errs315;
}
else {
vErrors = null;
}
}
}
if(errors === _errs313){
if(data158 && typeof data158 == "object" && !Array.isArray(data158)){
let missing31;
if((((data158.kind === undefined) && (missing31 = "kind")) || ((data158.domain === undefined) && (missing31 = "domain"))) || ((data158.code === undefined) && (missing31 = "code"))){
const err240 = {instancePath:instancePath+"/outcome",schemaPath:"#/$defs/rejection/required",keyword:"required",params:{missingProperty: missing31},message:"must have required property '"+missing31+"'"};
if(vErrors === null){
vErrors = [err240];
}
else {
vErrors.push(err240);
}
errors++;
}
else {
const _errs322 = errors;
for(const key31 in data158){
if(!(((key31 === "kind") || (key31 === "domain")) || (key31 === "code"))){
const err241 = {instancePath:instancePath+"/outcome",schemaPath:"#/$defs/rejection/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key31},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err241];
}
else {
vErrors.push(err241);
}
errors++;
break;
}
}
if(_errs322 === errors){
if(data158.kind !== undefined){
const _errs323 = errors;
if("rejected" !== data158.kind){
const err242 = {instancePath:instancePath+"/outcome/kind",schemaPath:"#/$defs/rejection/properties/kind/const",keyword:"const",params:{allowedValue: "rejected"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err242];
}
else {
vErrors.push(err242);
}
errors++;
}
var valid95 = _errs323 === errors;
}
else {
var valid95 = true;
}
if(valid95){
if(data158.domain !== undefined){
let data164 = data158.domain;
const _errs324 = errors;
if(!((data164 === "core") || (data164 === "auth"))){
const err243 = {instancePath:instancePath+"/outcome/domain",schemaPath:"#/$defs/rejection/properties/domain/enum",keyword:"enum",params:{allowedValues: schema160.properties.domain.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err243];
}
else {
vErrors.push(err243);
}
errors++;
}
var valid95 = _errs324 === errors;
}
else {
var valid95 = true;
}
if(valid95){
if(data158.code !== undefined){
const _errs325 = errors;
if(typeof data158.code !== "string"){
const err244 = {instancePath:instancePath+"/outcome/code",schemaPath:"#/$defs/rejection/properties/code/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err244];
}
else {
vErrors.push(err244);
}
errors++;
}
var valid95 = _errs325 === errors;
}
else {
var valid95 = true;
}
}
}
}
}
}
else {
const err245 = {instancePath:instancePath+"/outcome",schemaPath:"#/$defs/rejection/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err245];
}
else {
vErrors.push(err245);
}
errors++;
}
}
var _valid15 = _errs312 === errors;
if(_valid15 && valid90){
valid90 = false;
passing15 = [passing15, 1];
}
else {
if(_valid15){
valid90 = true;
passing15 = 1;
if(props15 !== true){
props15 = true;
}
}
}
if(!valid90){
const err246 = {instancePath:instancePath+"/outcome",schemaPath:"#/oneOf/13/properties/outcome/oneOf",keyword:"oneOf",params:{passingSchemas: passing15},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err246];
}
else {
vErrors.push(err246);
}
errors++;
}
else {
errors = _errs310;
if(vErrors !== null){
if(_errs310){
vErrors.length = _errs310;
}
else {
vErrors = null;
}
}
}
var valid82 = _errs309 === errors;
}
else {
var valid82 = true;
}
if(valid82){
if(data.commandReplayed !== undefined){
const _errs327 = errors;
if(typeof data.commandReplayed !== "boolean"){
const err247 = {instancePath:instancePath+"/commandReplayed",schemaPath:"#/oneOf/13/properties/commandReplayed/type",keyword:"type",params:{type: "boolean"},message:"must be boolean"};
if(vErrors === null){
vErrors = [err247];
}
else {
vErrors.push(err247);
}
errors++;
}
var valid82 = _errs327 === errors;
}
else {
var valid82 = true;
}
if(valid82){
if(data.publication !== undefined){
let data167 = data.publication;
const _errs329 = errors;
const _errs330 = errors;
let valid96 = false;
let passing17 = null;
const _errs331 = errors;
const _errs332 = errors;
const _errs334 = errors;
let valid98 = false;
let passing18 = null;
const _errs335 = errors;
if(data167 && typeof data167 == "object" && !Array.isArray(data167)){
if(data167.algorithm !== undefined){
const _errs336 = errors;
if("sha1" !== data167.algorithm){
const err248 = {instancePath:instancePath+"/publication/algorithm",schemaPath:"#/$defs/oid/oneOf/0/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha1"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err248];
}
else {
vErrors.push(err248);
}
errors++;
}
var valid99 = _errs336 === errors;
}
else {
var valid99 = true;
}
if(valid99){
if(data167.hex !== undefined){
let data169 = data167.hex;
const _errs337 = errors;
if(typeof data169 === "string"){
if(!pattern4.test(data169)){
const err249 = {instancePath:instancePath+"/publication/hex",schemaPath:"#/$defs/oid/oneOf/0/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{40}$"},message:"must match pattern \""+"^[0-9a-f]{40}$"+"\""};
if(vErrors === null){
vErrors = [err249];
}
else {
vErrors.push(err249);
}
errors++;
}
}
var valid99 = _errs337 === errors;
}
else {
var valid99 = true;
}
}
}
var _valid18 = _errs335 === errors;
if(_valid18){
valid98 = true;
passing18 = 0;
var props17 = {};
props17.algorithm = true;
props17.hex = true;
}
const _errs338 = errors;
if(data167 && typeof data167 == "object" && !Array.isArray(data167)){
if(data167.algorithm !== undefined){
const _errs339 = errors;
if("sha256" !== data167.algorithm){
const err250 = {instancePath:instancePath+"/publication/algorithm",schemaPath:"#/$defs/oid/oneOf/1/properties/algorithm/const",keyword:"const",params:{allowedValue: "sha256"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err250];
}
else {
vErrors.push(err250);
}
errors++;
}
var valid100 = _errs339 === errors;
}
else {
var valid100 = true;
}
if(valid100){
if(data167.hex !== undefined){
let data171 = data167.hex;
const _errs340 = errors;
if(typeof data171 === "string"){
if(!pattern5.test(data171)){
const err251 = {instancePath:instancePath+"/publication/hex",schemaPath:"#/$defs/oid/oneOf/1/properties/hex/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{64}$"},message:"must match pattern \""+"^[0-9a-f]{64}$"+"\""};
if(vErrors === null){
vErrors = [err251];
}
else {
vErrors.push(err251);
}
errors++;
}
}
var valid100 = _errs340 === errors;
}
else {
var valid100 = true;
}
}
}
var _valid18 = _errs338 === errors;
if(_valid18 && valid98){
valid98 = false;
passing18 = [passing18, 1];
}
else {
if(_valid18){
valid98 = true;
passing18 = 1;
if(props17 !== true){
props17 = props17 || {};
props17.algorithm = true;
props17.hex = true;
}
}
}
if(!valid98){
const err252 = {instancePath:instancePath+"/publication",schemaPath:"#/$defs/oid/oneOf",keyword:"oneOf",params:{passingSchemas: passing18},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err252];
}
else {
vErrors.push(err252);
}
errors++;
}
else {
errors = _errs334;
if(vErrors !== null){
if(_errs334){
vErrors.length = _errs334;
}
else {
vErrors = null;
}
}
}
if(errors === _errs332){
if(data167 && typeof data167 == "object" && !Array.isArray(data167)){
let missing32;
if(((data167.algorithm === undefined) && (missing32 = "algorithm")) || ((data167.hex === undefined) && (missing32 = "hex"))){
const err253 = {instancePath:instancePath+"/publication",schemaPath:"#/$defs/oid/required",keyword:"required",params:{missingProperty: missing32},message:"must have required property '"+missing32+"'"};
if(vErrors === null){
vErrors = [err253];
}
else {
vErrors.push(err253);
}
errors++;
}
else {
const _errs341 = errors;
for(const key32 in data167){
if(!((key32 === "algorithm") || (key32 === "hex"))){
const err254 = {instancePath:instancePath+"/publication",schemaPath:"#/$defs/oid/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key32},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err254];
}
else {
vErrors.push(err254);
}
errors++;
break;
}
}
if(_errs341 === errors){
if(data167.algorithm !== undefined){
let data172 = data167.algorithm;
const _errs342 = errors;
if(!((data172 === "sha1") || (data172 === "sha256"))){
const err255 = {instancePath:instancePath+"/publication/algorithm",schemaPath:"#/$defs/oid/properties/algorithm/enum",keyword:"enum",params:{allowedValues: schema33.properties.algorithm.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err255];
}
else {
vErrors.push(err255);
}
errors++;
}
var valid101 = _errs342 === errors;
}
else {
var valid101 = true;
}
if(valid101){
if(data167.hex !== undefined){
const _errs343 = errors;
if(typeof data167.hex !== "string"){
const err256 = {instancePath:instancePath+"/publication/hex",schemaPath:"#/$defs/oid/properties/hex/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err256];
}
else {
vErrors.push(err256);
}
errors++;
}
var valid101 = _errs343 === errors;
}
else {
var valid101 = true;
}
}
}
}
}
else {
const err257 = {instancePath:instancePath+"/publication",schemaPath:"#/$defs/oid/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err257];
}
else {
vErrors.push(err257);
}
errors++;
}
}
var _valid17 = _errs331 === errors;
if(_valid17){
valid96 = true;
passing17 = 0;
}
const _errs345 = errors;
if(data167 !== null){
const err258 = {instancePath:instancePath+"/publication",schemaPath:"#/oneOf/13/properties/publication/oneOf/1/type",keyword:"type",params:{type: "null"},message:"must be null"};
if(vErrors === null){
vErrors = [err258];
}
else {
vErrors.push(err258);
}
errors++;
}
var _valid17 = _errs345 === errors;
if(_valid17 && valid96){
valid96 = false;
passing17 = [passing17, 1];
}
else {
if(_valid17){
valid96 = true;
passing17 = 1;
}
}
if(!valid96){
const err259 = {instancePath:instancePath+"/publication",schemaPath:"#/oneOf/13/properties/publication/oneOf",keyword:"oneOf",params:{passingSchemas: passing17},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err259];
}
else {
vErrors.push(err259);
}
errors++;
}
else {
errors = _errs330;
if(vErrors !== null){
if(_errs330){
vErrors.length = _errs330;
}
else {
vErrors = null;
}
}
}
var valid82 = _errs329 === errors;
}
else {
var valid82 = true;
}
}
}
}
}
}
}
}
}
}
}
}
else {
const err260 = {instancePath,schemaPath:"#/oneOf/13/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err260];
}
else {
vErrors.push(err260);
}
errors++;
}
}
var _valid0 = _errs283 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 13];
}
else {
if(_valid0){
valid0 = true;
passing0 = 13;
if(props1 !== true){
props1 = true;
}
}
const _errs347 = errors;
if(errors === _errs347){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing33;
if((((data.formatVersion === undefined) && (missing33 = "formatVersion")) || ((data.kind === undefined) && (missing33 = "kind"))) || ((data.code === undefined) && (missing33 = "code"))){
const err261 = {instancePath,schemaPath:"#/oneOf/14/required",keyword:"required",params:{missingProperty: missing33},message:"must have required property '"+missing33+"'"};
if(vErrors === null){
vErrors = [err261];
}
else {
vErrors.push(err261);
}
errors++;
}
else {
const _errs349 = errors;
for(const key33 in data){
if(!(((key33 === "formatVersion") || (key33 === "kind")) || (key33 === "code"))){
const err262 = {instancePath,schemaPath:"#/oneOf/14/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key33},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err262];
}
else {
vErrors.push(err262);
}
errors++;
break;
}
}
if(_errs349 === errors){
if(data.formatVersion !== undefined){
const _errs350 = errors;
if(1 !== data.formatVersion){
const err263 = {instancePath:instancePath+"/formatVersion",schemaPath:"#/oneOf/14/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err263];
}
else {
vErrors.push(err263);
}
errors++;
}
var valid102 = _errs350 === errors;
}
else {
var valid102 = true;
}
if(valid102){
if(data.kind !== undefined){
const _errs351 = errors;
if("error" !== data.kind){
const err264 = {instancePath:instancePath+"/kind",schemaPath:"#/oneOf/14/properties/kind/const",keyword:"const",params:{allowedValue: "error"},message:"must be equal to constant"};
if(vErrors === null){
vErrors = [err264];
}
else {
vErrors.push(err264);
}
errors++;
}
var valid102 = _errs351 === errors;
}
else {
var valid102 = true;
}
if(valid102){
if(data.code !== undefined){
let data176 = data.code;
const _errs352 = errors;
if(!(((((((((((((data176 === "UNAUTHENTICATED") || (data176 === "FORBIDDEN")) || (data176 === "INVALID_INPUT")) || (data176 === "UNSUPPORTED")) || (data176 === "INGRESS_TOO_LARGE")) || (data176 === "RATE_LIMIT")) || (data176 === "UNAVAILABLE")) || (data176 === "SNAPSHOT_EXPIRED")) || (data176 === "NOT_FOUND")) || (data176 === "IDENTITY_CONFLICT")) || (data176 === "INTEGRITY")) || (data176 === "PUBLICATION_UNKNOWN")) || (data176 === "INCOMPLETE_PACKET"))){
const err265 = {instancePath:instancePath+"/code",schemaPath:"#/$defs/errorCode/enum",keyword:"enum",params:{allowedValues: schema162.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err265];
}
else {
vErrors.push(err265);
}
errors++;
}
var valid102 = _errs352 === errors;
}
else {
var valid102 = true;
}
}
}
}
}
}
else {
const err266 = {instancePath,schemaPath:"#/oneOf/14/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err266];
}
else {
vErrors.push(err266);
}
errors++;
}
}
var _valid0 = _errs347 === errors;
if(_valid0 && valid0){
valid0 = false;
passing0 = [passing0, 14];
}
else {
if(_valid0){
valid0 = true;
passing0 = 14;
if(props1 !== true){
props1 = true;
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
}
if(!valid0){
const err267 = {instancePath,schemaPath:"#/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err267];
}
else {
vErrors.push(err267);
}
errors++;
validate21.errors = vErrors;
return false;
}
else {
errors = _errs0;
if(vErrors !== null){
if(_errs0){
vErrors.length = _errs0;
}
else {
vErrors = null;
}
}
}
validate21.errors = vErrors;
evaluated0.props = props1;
return errors === 0;
}
validate21.evaluated = {"dynamicProps":true,"dynamicItems":false};


function validate20(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate20.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(!(data && typeof data == "object" && !Array.isArray(data))){
validate20.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
if(!(validate21(data, {instancePath,parentData,parentDataProperty,rootData,dynamicAnchors}))){
vErrors = vErrors === null ? validate21.errors : vErrors.concat(validate21.errors);
errors = vErrors.length;
}
else {
var props0 = validate21.evaluated.props;
}
validate20.errors = vErrors;
evaluated0.props = props0;
return errors === 0;
}
validate20.evaluated = {"dynamicProps":true,"dynamicItems":false};
