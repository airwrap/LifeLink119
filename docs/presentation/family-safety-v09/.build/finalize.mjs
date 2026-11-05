import path from 'node:path';
import {pathToFileURL} from 'node:url';
const skill='C:/Users/hotmh/.codex/plugins/cache/openai-primary-runtime/presentations/26.1020.11348/skills/presentations';
const workspace=path.resolve('docs/presentation/family-safety-v09');
const tmp=path.join(workspace,'.build');
const {finalizePresentation}=await import(pathToFileURL(path.join(skill,'container_tools/artifact_tool_utils.mjs')).href);
const result=await finalizePresentation({
 workspaceDir:workspace,candidatePath:path.join(tmp,'draft.pptx'),
 finalPath:path.join(workspace,'output','LifeLink119_가족안전_발표자료_v09.pptx'),
 pythonExecutable:'C:/Users/hotmh/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe',
 integrityValidatorPath:path.join(skill,'container_tools/inspect_presentation_package_integrity.py'),
 layoutValidatorPath:path.join(skill,'container_tools/inspect_presentation_layout_geometry.py'),
 layoutArgs:['--expected-slide-size-emu','12192000,6858000','--validate-bullet-geometry','--validate-heading-fit'],
 explicitTotalSlideCount:10,requiredNativeTableOwnerSlides:[],requiredNativeChartOwnerSlides:[],
 fontPolicy:{basis:'design',families:['Malgun Gothic']},verifyArtifactToolImport:true,
 receiptPath:path.join(tmp,'validation.json')
});
console.log(JSON.stringify(result,null,2));
