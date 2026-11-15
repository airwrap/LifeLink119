import path from 'node:path';
import fs from 'node:fs/promises';
import { pathToFileURL, fileURLToPath } from 'node:url';
const BUILD=path.dirname(fileURLToPath(import.meta.url));
const BASE=path.dirname(BUILD);
const SKILL=process.env.LIFELINK_PRESENTATIONS_SKILL;
if(!SKILL) throw new Error('Set LIFELINK_PRESENTATIONS_SKILL to the installed Presentations skill directory.');
const {finalizePresentation}=await import(pathToFileURL(path.join(SKILL,'container_tools/artifact_tool_utils.mjs')).href);
await fs.mkdir(path.join(BASE,'output'),{recursive:true});
const result=await finalizePresentation({
  workspaceDir:BASE,
  candidatePath:path.join(BUILD,'candidate.pptx'),
  finalPath:path.join(BASE,'output','LifeLink119_음성중심_사용안내_현대차가족.pptx'),
  pythonExecutable:process.env.LIFELINK_PYTHON || 'python',
  integrityValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_package_integrity.py'),
  layoutValidatorPath:path.join(SKILL,'container_tools/inspect_presentation_layout_geometry.py'),
  layoutArgs:['--expected-slide-size-emu','15240000,8572500','--validate-bullet-geometry','--validate-heading-fit','--require-native-table-slide','12'],
  requiredNativeTableOwnerSlides:[12],
  requiredNativeChartOwnerSlides:[],
  fontPolicy:{basis:'design',families:['Malgun Gothic']},
  // The package/runtime is unavailable on this host. Do not claim an import test.
  verifyArtifactToolImport:false,
  receiptPath:path.join(BUILD,'finalization-receipt-v2.json'),
});
console.log(JSON.stringify(result));
