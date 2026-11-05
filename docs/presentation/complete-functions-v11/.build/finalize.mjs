import fs from 'node:fs/promises';import path from 'node:path';import {pathToFileURL} from 'node:url';
const skill='C:/Users/hotmh/.codex/plugins/cache/openai-primary-runtime/presentations/26.1020.11348/skills/presentations';
const workspace=path.resolve('docs/presentation/complete-functions-v11'),tmp=path.join(workspace,'.build');
const notes=JSON.parse(await fs.readFile(path.join(tmp,'notes.json'),'utf8'));
const {finalizePresentation}=await import(pathToFileURL(path.join(skill,'container_tools/artifact_tool_utils.mjs')).href);
const result=await finalizePresentation({workspaceDir:workspace,candidatePath:path.join(tmp,'draft.pptx'),
 finalPath:path.join(workspace,'output','LifeLink119_전체기능_UI_소개_v11.pptx'),
 pythonExecutable:'C:/Users/hotmh/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',
 integrityValidatorPath:path.join(skill,'container_tools/inspect_presentation_package_integrity.py'),
 layoutValidatorPath:path.join(skill,'container_tools/inspect_presentation_layout_geometry.py'),
 layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit'],
 explicitTotalSlideCount:notes.length,requiredNativeTableOwnerSlides:[],requiredNativeChartOwnerSlides:[],
 fontPolicy:{basis:'design',families:['Malgun Gothic']},verifyArtifactToolImport:true,
 receiptPath:path.join(tmp,'validation.json')});
console.log(JSON.stringify({finalPath:result.finalPath,sha256:result.finalSha256,slides:notes.length,layout:result.presentationLayout?.status,missing:JSON.parse(await fs.readFile(path.join(tmp,'missing.json'),'utf8'))},null,2));
