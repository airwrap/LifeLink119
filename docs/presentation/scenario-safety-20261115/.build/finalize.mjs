import path from 'node:path';
import fs from 'node:fs/promises';
import {pathToFileURL,fileURLToPath} from 'node:url';
const BUILD=path.dirname(fileURLToPath(import.meta.url)),BASE=path.dirname(BUILD);
const SKILL=process.env.LIFELINK_PRESENTATIONS_SKILL;
if(!SKILL) throw new Error('Set LIFELINK_PRESENTATIONS_SKILL to the installed Presentations skill directory.');
const {finalizePresentation}=await import(pathToFileURL(path.join(SKILL,'container_tools/artifact_tool_utils.mjs')).href);
await fs.mkdir(path.join(BASE,'output'),{recursive:true});
const result=await finalizePresentation({
  workspaceDir:BASE,
  candidatePath:path.join(BUILD,'candidate.pptx'),
  finalPath:path.join(BASE,'output','LifeLink119_상황별안내와_탑승자안전.pptx'),
  pythonExecutable:process.env.LIFELINK_PYTHON || 'python',
  integrityValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_package_integrity.py'),
  layoutValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_layout_geometry.py'),
  layoutArgs:['--expected-slide-size-emu','15240000,8572500','--validate-bullet-geometry','--validate-heading-fit','--require-native-table-slide','19','--require-native-table-slide','23'],
  requiredNativeTableOwnerSlides:[19,23],requiredNativeChartOwnerSlides:[],
  fontPolicy:{basis:'design',families:['Malgun Gothic']},
  // The host has no Artifact Tool runtime. This file records structural checks only.
  verifyArtifactToolImport:false,
  receiptPath:path.join(BUILD,'finalization-receipt.json'),
});
console.log(JSON.stringify(result));
