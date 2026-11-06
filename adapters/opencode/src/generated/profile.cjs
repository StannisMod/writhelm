// Generated from the pinned Writhelm schema by Ajv 8.17.1.
"use strict";
module.exports = validate152;
module.exports.default = validate152;
const schema163 = {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object","additionalProperties":false,"properties":{"formatVersion":{"const":1},"origin":{"type":"string","maxLength":4096},"storeId":{"type":"string","pattern":"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},"projectId":{"type":"string","pattern":"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},"principalId":{"type":"string","pattern":"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},"active":{"type":"object","additionalProperties":false,"properties":{"credentialId":{"type":"string","pattern":"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},"token":{"type":"string","minLength":121,"maxLength":121}},"required":["credentialId","token"]},"agent":{"type":"object","additionalProperties":false,"properties":{"principalId":{"type":"string","pattern":"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},"credentialId":{"type":"string","pattern":"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},"token":{"type":"string","minLength":121,"maxLength":121}},"required":["principalId","credentialId","token"]},"pending":{"oneOf":[{"type":"object","additionalProperties":false,"properties":{"kind":{"enum":["answer","rotation","setup"]},"session":{"type":"object","additionalProperties":false,"properties":{"key":{"type":"string","maxLength":256},"id":{"type":"string","maxLength":256},"server":{"type":"string","maxLength":256},"directory":{"type":"string","maxLength":4096},"projectRoot":{"type":"string","maxLength":4096}},"required":["key","id","server","directory","projectRoot"]},"commandJSON":{"type":"string","maxLength":98304},"replacement":{"oneOf":[{"type":"object","additionalProperties":false,"properties":{"credentialId":{"type":"string","pattern":"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},"token":{"type":"string","minLength":121,"maxLength":121}},"required":["credentialId","token"]},{"type":"null"}]},"registrationJSON":{"type":"string","maxLength":16384},"storeDirectory":{"type":"string","maxLength":4096}},"required":["kind","session","commandJSON","replacement","registrationJSON","storeDirectory"]},{"type":"null"}]}},"required":["formatVersion","origin","storeId","projectId","principalId","active","agent","pending"]};
const func1 = require("ajv/dist/runtime/ucs2length").default;
const pattern6 = new RegExp("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", "u");

function validate152(data, {instancePath="", parentData, parentDataProperty, rootData=data, dynamicAnchors={}}={}){
let vErrors = null;
let errors = 0;
const evaluated0 = validate152.evaluated;
if(evaluated0.dynamicProps){
evaluated0.props = undefined;
}
if(evaluated0.dynamicItems){
evaluated0.items = undefined;
}
if(errors === 0){
if(data && typeof data == "object" && !Array.isArray(data)){
let missing0;
if(((((((((data.formatVersion === undefined) && (missing0 = "formatVersion")) || ((data.origin === undefined) && (missing0 = "origin"))) || ((data.storeId === undefined) && (missing0 = "storeId"))) || ((data.projectId === undefined) && (missing0 = "projectId"))) || ((data.principalId === undefined) && (missing0 = "principalId"))) || ((data.active === undefined) && (missing0 = "active"))) || ((data.agent === undefined) && (missing0 = "agent"))) || ((data.pending === undefined) && (missing0 = "pending"))){
validate152.errors = [{instancePath,schemaPath:"#/required",keyword:"required",params:{missingProperty: missing0},message:"must have required property '"+missing0+"'"}];
return false;
}
else {
const _errs1 = errors;
for(const key0 in data){
if(!((((((((key0 === "formatVersion") || (key0 === "origin")) || (key0 === "storeId")) || (key0 === "projectId")) || (key0 === "principalId")) || (key0 === "active")) || (key0 === "agent")) || (key0 === "pending"))){
validate152.errors = [{instancePath,schemaPath:"#/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key0},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs1 === errors){
if(data.formatVersion !== undefined){
const _errs2 = errors;
if(1 !== data.formatVersion){
validate152.errors = [{instancePath:instancePath+"/formatVersion",schemaPath:"#/properties/formatVersion/const",keyword:"const",params:{allowedValue: 1},message:"must be equal to constant"}];
return false;
}
var valid0 = _errs2 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.origin !== undefined){
let data1 = data.origin;
const _errs3 = errors;
if(errors === _errs3){
if(typeof data1 === "string"){
if(func1(data1) > 4096){
validate152.errors = [{instancePath:instancePath+"/origin",schemaPath:"#/properties/origin/maxLength",keyword:"maxLength",params:{limit: 4096},message:"must NOT have more than 4096 characters"}];
return false;
}
}
else {
validate152.errors = [{instancePath:instancePath+"/origin",schemaPath:"#/properties/origin/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs3 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.storeId !== undefined){
let data2 = data.storeId;
const _errs5 = errors;
if(errors === _errs5){
if(typeof data2 === "string"){
if(!pattern6.test(data2)){
validate152.errors = [{instancePath:instancePath+"/storeId",schemaPath:"#/properties/storeId/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate152.errors = [{instancePath:instancePath+"/storeId",schemaPath:"#/properties/storeId/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs5 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.projectId !== undefined){
let data3 = data.projectId;
const _errs7 = errors;
if(errors === _errs7){
if(typeof data3 === "string"){
if(!pattern6.test(data3)){
validate152.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/properties/projectId/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate152.errors = [{instancePath:instancePath+"/projectId",schemaPath:"#/properties/projectId/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs7 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.principalId !== undefined){
let data4 = data.principalId;
const _errs9 = errors;
if(errors === _errs9){
if(typeof data4 === "string"){
if(!pattern6.test(data4)){
validate152.errors = [{instancePath:instancePath+"/principalId",schemaPath:"#/properties/principalId/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate152.errors = [{instancePath:instancePath+"/principalId",schemaPath:"#/properties/principalId/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid0 = _errs9 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.active !== undefined){
let data5 = data.active;
const _errs11 = errors;
if(errors === _errs11){
if(data5 && typeof data5 == "object" && !Array.isArray(data5)){
let missing1;
if(((data5.credentialId === undefined) && (missing1 = "credentialId")) || ((data5.token === undefined) && (missing1 = "token"))){
validate152.errors = [{instancePath:instancePath+"/active",schemaPath:"#/properties/active/required",keyword:"required",params:{missingProperty: missing1},message:"must have required property '"+missing1+"'"}];
return false;
}
else {
const _errs13 = errors;
for(const key1 in data5){
if(!((key1 === "credentialId") || (key1 === "token"))){
validate152.errors = [{instancePath:instancePath+"/active",schemaPath:"#/properties/active/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key1},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs13 === errors){
if(data5.credentialId !== undefined){
let data6 = data5.credentialId;
const _errs14 = errors;
if(errors === _errs14){
if(typeof data6 === "string"){
if(!pattern6.test(data6)){
validate152.errors = [{instancePath:instancePath+"/active/credentialId",schemaPath:"#/properties/active/properties/credentialId/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate152.errors = [{instancePath:instancePath+"/active/credentialId",schemaPath:"#/properties/active/properties/credentialId/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid1 = _errs14 === errors;
}
else {
var valid1 = true;
}
if(valid1){
if(data5.token !== undefined){
let data7 = data5.token;
const _errs16 = errors;
if(errors === _errs16){
if(typeof data7 === "string"){
if(func1(data7) > 121){
validate152.errors = [{instancePath:instancePath+"/active/token",schemaPath:"#/properties/active/properties/token/maxLength",keyword:"maxLength",params:{limit: 121},message:"must NOT have more than 121 characters"}];
return false;
}
else {
if(func1(data7) < 121){
validate152.errors = [{instancePath:instancePath+"/active/token",schemaPath:"#/properties/active/properties/token/minLength",keyword:"minLength",params:{limit: 121},message:"must NOT have fewer than 121 characters"}];
return false;
}
}
}
else {
validate152.errors = [{instancePath:instancePath+"/active/token",schemaPath:"#/properties/active/properties/token/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid1 = _errs16 === errors;
}
else {
var valid1 = true;
}
}
}
}
}
else {
validate152.errors = [{instancePath:instancePath+"/active",schemaPath:"#/properties/active/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
var valid0 = _errs11 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.agent !== undefined){
let data8 = data.agent;
const _errs18 = errors;
if(errors === _errs18){
if(data8 && typeof data8 == "object" && !Array.isArray(data8)){
let missing2;
if((((data8.principalId === undefined) && (missing2 = "principalId")) || ((data8.credentialId === undefined) && (missing2 = "credentialId"))) || ((data8.token === undefined) && (missing2 = "token"))){
validate152.errors = [{instancePath:instancePath+"/agent",schemaPath:"#/properties/agent/required",keyword:"required",params:{missingProperty: missing2},message:"must have required property '"+missing2+"'"}];
return false;
}
else {
const _errs20 = errors;
for(const key2 in data8){
if(!(((key2 === "principalId") || (key2 === "credentialId")) || (key2 === "token"))){
validate152.errors = [{instancePath:instancePath+"/agent",schemaPath:"#/properties/agent/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key2},message:"must NOT have additional properties"}];
return false;
break;
}
}
if(_errs20 === errors){
if(data8.principalId !== undefined){
let data9 = data8.principalId;
const _errs21 = errors;
if(errors === _errs21){
if(typeof data9 === "string"){
if(!pattern6.test(data9)){
validate152.errors = [{instancePath:instancePath+"/agent/principalId",schemaPath:"#/properties/agent/properties/principalId/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate152.errors = [{instancePath:instancePath+"/agent/principalId",schemaPath:"#/properties/agent/properties/principalId/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid2 = _errs21 === errors;
}
else {
var valid2 = true;
}
if(valid2){
if(data8.credentialId !== undefined){
let data10 = data8.credentialId;
const _errs23 = errors;
if(errors === _errs23){
if(typeof data10 === "string"){
if(!pattern6.test(data10)){
validate152.errors = [{instancePath:instancePath+"/agent/credentialId",schemaPath:"#/properties/agent/properties/credentialId/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""}];
return false;
}
}
else {
validate152.errors = [{instancePath:instancePath+"/agent/credentialId",schemaPath:"#/properties/agent/properties/credentialId/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid2 = _errs23 === errors;
}
else {
var valid2 = true;
}
if(valid2){
if(data8.token !== undefined){
let data11 = data8.token;
const _errs25 = errors;
if(errors === _errs25){
if(typeof data11 === "string"){
if(func1(data11) > 121){
validate152.errors = [{instancePath:instancePath+"/agent/token",schemaPath:"#/properties/agent/properties/token/maxLength",keyword:"maxLength",params:{limit: 121},message:"must NOT have more than 121 characters"}];
return false;
}
else {
if(func1(data11) < 121){
validate152.errors = [{instancePath:instancePath+"/agent/token",schemaPath:"#/properties/agent/properties/token/minLength",keyword:"minLength",params:{limit: 121},message:"must NOT have fewer than 121 characters"}];
return false;
}
}
}
else {
validate152.errors = [{instancePath:instancePath+"/agent/token",schemaPath:"#/properties/agent/properties/token/type",keyword:"type",params:{type: "string"},message:"must be string"}];
return false;
}
}
var valid2 = _errs25 === errors;
}
else {
var valid2 = true;
}
}
}
}
}
}
else {
validate152.errors = [{instancePath:instancePath+"/agent",schemaPath:"#/properties/agent/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
var valid0 = _errs18 === errors;
}
else {
var valid0 = true;
}
if(valid0){
if(data.pending !== undefined){
let data12 = data.pending;
const _errs27 = errors;
const _errs28 = errors;
let valid3 = false;
let passing0 = null;
const _errs29 = errors;
if(errors === _errs29){
if(data12 && typeof data12 == "object" && !Array.isArray(data12)){
let missing3;
if(((((((data12.kind === undefined) && (missing3 = "kind")) || ((data12.session === undefined) && (missing3 = "session"))) || ((data12.commandJSON === undefined) && (missing3 = "commandJSON"))) || ((data12.replacement === undefined) && (missing3 = "replacement"))) || ((data12.registrationJSON === undefined) && (missing3 = "registrationJSON"))) || ((data12.storeDirectory === undefined) && (missing3 = "storeDirectory"))){
const err0 = {instancePath:instancePath+"/pending",schemaPath:"#/properties/pending/oneOf/0/required",keyword:"required",params:{missingProperty: missing3},message:"must have required property '"+missing3+"'"};
if(vErrors === null){
vErrors = [err0];
}
else {
vErrors.push(err0);
}
errors++;
}
else {
const _errs31 = errors;
for(const key3 in data12){
if(!((((((key3 === "kind") || (key3 === "session")) || (key3 === "commandJSON")) || (key3 === "replacement")) || (key3 === "registrationJSON")) || (key3 === "storeDirectory"))){
const err1 = {instancePath:instancePath+"/pending",schemaPath:"#/properties/pending/oneOf/0/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key3},message:"must NOT have additional properties"};
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
if(_errs31 === errors){
if(data12.kind !== undefined){
let data13 = data12.kind;
const _errs32 = errors;
if(!(((data13 === "answer") || (data13 === "rotation")) || (data13 === "setup"))){
const err2 = {instancePath:instancePath+"/pending/kind",schemaPath:"#/properties/pending/oneOf/0/properties/kind/enum",keyword:"enum",params:{allowedValues: schema163.properties.pending.oneOf[0].properties.kind.enum},message:"must be equal to one of the allowed values"};
if(vErrors === null){
vErrors = [err2];
}
else {
vErrors.push(err2);
}
errors++;
}
var valid4 = _errs32 === errors;
}
else {
var valid4 = true;
}
if(valid4){
if(data12.session !== undefined){
let data14 = data12.session;
const _errs33 = errors;
if(errors === _errs33){
if(data14 && typeof data14 == "object" && !Array.isArray(data14)){
let missing4;
if((((((data14.key === undefined) && (missing4 = "key")) || ((data14.id === undefined) && (missing4 = "id"))) || ((data14.server === undefined) && (missing4 = "server"))) || ((data14.directory === undefined) && (missing4 = "directory"))) || ((data14.projectRoot === undefined) && (missing4 = "projectRoot"))){
const err3 = {instancePath:instancePath+"/pending/session",schemaPath:"#/properties/pending/oneOf/0/properties/session/required",keyword:"required",params:{missingProperty: missing4},message:"must have required property '"+missing4+"'"};
if(vErrors === null){
vErrors = [err3];
}
else {
vErrors.push(err3);
}
errors++;
}
else {
const _errs35 = errors;
for(const key4 in data14){
if(!(((((key4 === "key") || (key4 === "id")) || (key4 === "server")) || (key4 === "directory")) || (key4 === "projectRoot"))){
const err4 = {instancePath:instancePath+"/pending/session",schemaPath:"#/properties/pending/oneOf/0/properties/session/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key4},message:"must NOT have additional properties"};
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
if(_errs35 === errors){
if(data14.key !== undefined){
let data15 = data14.key;
const _errs36 = errors;
if(errors === _errs36){
if(typeof data15 === "string"){
if(func1(data15) > 256){
const err5 = {instancePath:instancePath+"/pending/session/key",schemaPath:"#/properties/pending/oneOf/0/properties/session/properties/key/maxLength",keyword:"maxLength",params:{limit: 256},message:"must NOT have more than 256 characters"};
if(vErrors === null){
vErrors = [err5];
}
else {
vErrors.push(err5);
}
errors++;
}
}
else {
const err6 = {instancePath:instancePath+"/pending/session/key",schemaPath:"#/properties/pending/oneOf/0/properties/session/properties/key/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err6];
}
else {
vErrors.push(err6);
}
errors++;
}
}
var valid5 = _errs36 === errors;
}
else {
var valid5 = true;
}
if(valid5){
if(data14.id !== undefined){
let data16 = data14.id;
const _errs38 = errors;
if(errors === _errs38){
if(typeof data16 === "string"){
if(func1(data16) > 256){
const err7 = {instancePath:instancePath+"/pending/session/id",schemaPath:"#/properties/pending/oneOf/0/properties/session/properties/id/maxLength",keyword:"maxLength",params:{limit: 256},message:"must NOT have more than 256 characters"};
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
const err8 = {instancePath:instancePath+"/pending/session/id",schemaPath:"#/properties/pending/oneOf/0/properties/session/properties/id/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err8];
}
else {
vErrors.push(err8);
}
errors++;
}
}
var valid5 = _errs38 === errors;
}
else {
var valid5 = true;
}
if(valid5){
if(data14.server !== undefined){
let data17 = data14.server;
const _errs40 = errors;
if(errors === _errs40){
if(typeof data17 === "string"){
if(func1(data17) > 256){
const err9 = {instancePath:instancePath+"/pending/session/server",schemaPath:"#/properties/pending/oneOf/0/properties/session/properties/server/maxLength",keyword:"maxLength",params:{limit: 256},message:"must NOT have more than 256 characters"};
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
const err10 = {instancePath:instancePath+"/pending/session/server",schemaPath:"#/properties/pending/oneOf/0/properties/session/properties/server/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err10];
}
else {
vErrors.push(err10);
}
errors++;
}
}
var valid5 = _errs40 === errors;
}
else {
var valid5 = true;
}
if(valid5){
if(data14.directory !== undefined){
let data18 = data14.directory;
const _errs42 = errors;
if(errors === _errs42){
if(typeof data18 === "string"){
if(func1(data18) > 4096){
const err11 = {instancePath:instancePath+"/pending/session/directory",schemaPath:"#/properties/pending/oneOf/0/properties/session/properties/directory/maxLength",keyword:"maxLength",params:{limit: 4096},message:"must NOT have more than 4096 characters"};
if(vErrors === null){
vErrors = [err11];
}
else {
vErrors.push(err11);
}
errors++;
}
}
else {
const err12 = {instancePath:instancePath+"/pending/session/directory",schemaPath:"#/properties/pending/oneOf/0/properties/session/properties/directory/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err12];
}
else {
vErrors.push(err12);
}
errors++;
}
}
var valid5 = _errs42 === errors;
}
else {
var valid5 = true;
}
if(valid5){
if(data14.projectRoot !== undefined){
let data19 = data14.projectRoot;
const _errs44 = errors;
if(errors === _errs44){
if(typeof data19 === "string"){
if(func1(data19) > 4096){
const err13 = {instancePath:instancePath+"/pending/session/projectRoot",schemaPath:"#/properties/pending/oneOf/0/properties/session/properties/projectRoot/maxLength",keyword:"maxLength",params:{limit: 4096},message:"must NOT have more than 4096 characters"};
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
const err14 = {instancePath:instancePath+"/pending/session/projectRoot",schemaPath:"#/properties/pending/oneOf/0/properties/session/properties/projectRoot/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err14];
}
else {
vErrors.push(err14);
}
errors++;
}
}
var valid5 = _errs44 === errors;
}
else {
var valid5 = true;
}
}
}
}
}
}
}
}
else {
const err15 = {instancePath:instancePath+"/pending/session",schemaPath:"#/properties/pending/oneOf/0/properties/session/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err15];
}
else {
vErrors.push(err15);
}
errors++;
}
}
var valid4 = _errs33 === errors;
}
else {
var valid4 = true;
}
if(valid4){
if(data12.commandJSON !== undefined){
let data20 = data12.commandJSON;
const _errs46 = errors;
if(errors === _errs46){
if(typeof data20 === "string"){
if(func1(data20) > 98304){
const err16 = {instancePath:instancePath+"/pending/commandJSON",schemaPath:"#/properties/pending/oneOf/0/properties/commandJSON/maxLength",keyword:"maxLength",params:{limit: 98304},message:"must NOT have more than 98304 characters"};
if(vErrors === null){
vErrors = [err16];
}
else {
vErrors.push(err16);
}
errors++;
}
}
else {
const err17 = {instancePath:instancePath+"/pending/commandJSON",schemaPath:"#/properties/pending/oneOf/0/properties/commandJSON/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err17];
}
else {
vErrors.push(err17);
}
errors++;
}
}
var valid4 = _errs46 === errors;
}
else {
var valid4 = true;
}
if(valid4){
if(data12.replacement !== undefined){
let data21 = data12.replacement;
const _errs48 = errors;
const _errs49 = errors;
let valid6 = false;
let passing1 = null;
const _errs50 = errors;
if(errors === _errs50){
if(data21 && typeof data21 == "object" && !Array.isArray(data21)){
let missing5;
if(((data21.credentialId === undefined) && (missing5 = "credentialId")) || ((data21.token === undefined) && (missing5 = "token"))){
const err18 = {instancePath:instancePath+"/pending/replacement",schemaPath:"#/properties/pending/oneOf/0/properties/replacement/oneOf/0/required",keyword:"required",params:{missingProperty: missing5},message:"must have required property '"+missing5+"'"};
if(vErrors === null){
vErrors = [err18];
}
else {
vErrors.push(err18);
}
errors++;
}
else {
const _errs52 = errors;
for(const key5 in data21){
if(!((key5 === "credentialId") || (key5 === "token"))){
const err19 = {instancePath:instancePath+"/pending/replacement",schemaPath:"#/properties/pending/oneOf/0/properties/replacement/oneOf/0/additionalProperties",keyword:"additionalProperties",params:{additionalProperty: key5},message:"must NOT have additional properties"};
if(vErrors === null){
vErrors = [err19];
}
else {
vErrors.push(err19);
}
errors++;
break;
}
}
if(_errs52 === errors){
if(data21.credentialId !== undefined){
let data22 = data21.credentialId;
const _errs53 = errors;
if(errors === _errs53){
if(typeof data22 === "string"){
if(!pattern6.test(data22)){
const err20 = {instancePath:instancePath+"/pending/replacement/credentialId",schemaPath:"#/properties/pending/oneOf/0/properties/replacement/oneOf/0/properties/credentialId/pattern",keyword:"pattern",params:{pattern: "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"},message:"must match pattern \""+"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"+"\""};
if(vErrors === null){
vErrors = [err20];
}
else {
vErrors.push(err20);
}
errors++;
}
}
else {
const err21 = {instancePath:instancePath+"/pending/replacement/credentialId",schemaPath:"#/properties/pending/oneOf/0/properties/replacement/oneOf/0/properties/credentialId/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err21];
}
else {
vErrors.push(err21);
}
errors++;
}
}
var valid7 = _errs53 === errors;
}
else {
var valid7 = true;
}
if(valid7){
if(data21.token !== undefined){
let data23 = data21.token;
const _errs55 = errors;
if(errors === _errs55){
if(typeof data23 === "string"){
if(func1(data23) > 121){
const err22 = {instancePath:instancePath+"/pending/replacement/token",schemaPath:"#/properties/pending/oneOf/0/properties/replacement/oneOf/0/properties/token/maxLength",keyword:"maxLength",params:{limit: 121},message:"must NOT have more than 121 characters"};
if(vErrors === null){
vErrors = [err22];
}
else {
vErrors.push(err22);
}
errors++;
}
else {
if(func1(data23) < 121){
const err23 = {instancePath:instancePath+"/pending/replacement/token",schemaPath:"#/properties/pending/oneOf/0/properties/replacement/oneOf/0/properties/token/minLength",keyword:"minLength",params:{limit: 121},message:"must NOT have fewer than 121 characters"};
if(vErrors === null){
vErrors = [err23];
}
else {
vErrors.push(err23);
}
errors++;
}
}
}
else {
const err24 = {instancePath:instancePath+"/pending/replacement/token",schemaPath:"#/properties/pending/oneOf/0/properties/replacement/oneOf/0/properties/token/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err24];
}
else {
vErrors.push(err24);
}
errors++;
}
}
var valid7 = _errs55 === errors;
}
else {
var valid7 = true;
}
}
}
}
}
else {
const err25 = {instancePath:instancePath+"/pending/replacement",schemaPath:"#/properties/pending/oneOf/0/properties/replacement/oneOf/0/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err25];
}
else {
vErrors.push(err25);
}
errors++;
}
}
var _valid1 = _errs50 === errors;
if(_valid1){
valid6 = true;
passing1 = 0;
}
const _errs57 = errors;
if(data21 !== null){
const err26 = {instancePath:instancePath+"/pending/replacement",schemaPath:"#/properties/pending/oneOf/0/properties/replacement/oneOf/1/type",keyword:"type",params:{type: "null"},message:"must be null"};
if(vErrors === null){
vErrors = [err26];
}
else {
vErrors.push(err26);
}
errors++;
}
var _valid1 = _errs57 === errors;
if(_valid1 && valid6){
valid6 = false;
passing1 = [passing1, 1];
}
else {
if(_valid1){
valid6 = true;
passing1 = 1;
}
}
if(!valid6){
const err27 = {instancePath:instancePath+"/pending/replacement",schemaPath:"#/properties/pending/oneOf/0/properties/replacement/oneOf",keyword:"oneOf",params:{passingSchemas: passing1},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err27];
}
else {
vErrors.push(err27);
}
errors++;
}
else {
errors = _errs49;
if(vErrors !== null){
if(_errs49){
vErrors.length = _errs49;
}
else {
vErrors = null;
}
}
}
var valid4 = _errs48 === errors;
}
else {
var valid4 = true;
}
if(valid4){
if(data12.registrationJSON !== undefined){
let data24 = data12.registrationJSON;
const _errs59 = errors;
if(errors === _errs59){
if(typeof data24 === "string"){
if(func1(data24) > 16384){
const err28 = {instancePath:instancePath+"/pending/registrationJSON",schemaPath:"#/properties/pending/oneOf/0/properties/registrationJSON/maxLength",keyword:"maxLength",params:{limit: 16384},message:"must NOT have more than 16384 characters"};
if(vErrors === null){
vErrors = [err28];
}
else {
vErrors.push(err28);
}
errors++;
}
}
else {
const err29 = {instancePath:instancePath+"/pending/registrationJSON",schemaPath:"#/properties/pending/oneOf/0/properties/registrationJSON/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err29];
}
else {
vErrors.push(err29);
}
errors++;
}
}
var valid4 = _errs59 === errors;
}
else {
var valid4 = true;
}
if(valid4){
if(data12.storeDirectory !== undefined){
let data25 = data12.storeDirectory;
const _errs61 = errors;
if(errors === _errs61){
if(typeof data25 === "string"){
if(func1(data25) > 4096){
const err30 = {instancePath:instancePath+"/pending/storeDirectory",schemaPath:"#/properties/pending/oneOf/0/properties/storeDirectory/maxLength",keyword:"maxLength",params:{limit: 4096},message:"must NOT have more than 4096 characters"};
if(vErrors === null){
vErrors = [err30];
}
else {
vErrors.push(err30);
}
errors++;
}
}
else {
const err31 = {instancePath:instancePath+"/pending/storeDirectory",schemaPath:"#/properties/pending/oneOf/0/properties/storeDirectory/type",keyword:"type",params:{type: "string"},message:"must be string"};
if(vErrors === null){
vErrors = [err31];
}
else {
vErrors.push(err31);
}
errors++;
}
}
var valid4 = _errs61 === errors;
}
else {
var valid4 = true;
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
const err32 = {instancePath:instancePath+"/pending",schemaPath:"#/properties/pending/oneOf/0/type",keyword:"type",params:{type: "object"},message:"must be object"};
if(vErrors === null){
vErrors = [err32];
}
else {
vErrors.push(err32);
}
errors++;
}
}
var _valid0 = _errs29 === errors;
if(_valid0){
valid3 = true;
passing0 = 0;
}
const _errs63 = errors;
if(data12 !== null){
const err33 = {instancePath:instancePath+"/pending",schemaPath:"#/properties/pending/oneOf/1/type",keyword:"type",params:{type: "null"},message:"must be null"};
if(vErrors === null){
vErrors = [err33];
}
else {
vErrors.push(err33);
}
errors++;
}
var _valid0 = _errs63 === errors;
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
const err34 = {instancePath:instancePath+"/pending",schemaPath:"#/properties/pending/oneOf",keyword:"oneOf",params:{passingSchemas: passing0},message:"must match exactly one schema in oneOf"};
if(vErrors === null){
vErrors = [err34];
}
else {
vErrors.push(err34);
}
errors++;
validate152.errors = vErrors;
return false;
}
else {
errors = _errs28;
if(vErrors !== null){
if(_errs28){
vErrors.length = _errs28;
}
else {
vErrors = null;
}
}
}
var valid0 = _errs27 === errors;
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
validate152.errors = [{instancePath,schemaPath:"#/type",keyword:"type",params:{type: "object"},message:"must be object"}];
return false;
}
}
validate152.errors = vErrors;
return errors === 0;
}
validate152.evaluated = {"props":true,"dynamicProps":false,"dynamicItems":false};
