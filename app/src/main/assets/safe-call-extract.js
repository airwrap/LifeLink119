/* Read only the user's current u119 form, on an explicit native button press.
 * No fetch, cookies, WebSquare data models, credentials, form submission or JS bridge.
 * IDs/labels were inspected in the user-supplied 2026-11-16 MHT. */
(() => {
  'use strict';
  let currentField = '';
  const fail = code => JSON.stringify({schema: 1, error: code, field: currentField});
  if (location.origin !== 'https://u119.nfa.go.kr' || window !== window.top) return fail('ORIGIN');
  const prefix = 'mf_wfm_container_wfm_userInfo_';
  const fields = {}, missing = [];
  const clean = value => {
    const text = String(value == null ? '' : value).trim();
    if (text.length > 4000 || /[\u0000-\u0008\u000b\u000c\u000e-\u001f\u007f\u202a-\u202e\u2066-\u2069]/.test(text)) throw Error('VALUE');
    return text;
  };
  const node = suffix => {
    const matches = document.querySelectorAll('[id="' + prefix + suffix + '"]');
    if (matches.length > 1) throw Error('STRUCTURE');
    return matches[0] || null;
  };
  const visible = e => {
    for (let p = e; p; p = p.parentElement) {
      const s = getComputedStyle(p);
      if (s.display === 'none' || s.visibility === 'hidden') return false;
    }
    return true;
  };
  const input = suffix => {
    const e = node(suffix);
    if (!e) return null;
    if (!['INPUT', 'TEXTAREA'].includes(e.tagName) || !['text', 'tel', 'textarea'].includes(e.type)) throw Error('STRUCTURE');
    // .value includes live form values; MHT value attributes may be empty.
    return clean(e.value);
  };
  const put = (key, read) => {
    currentField = key;
    const value = read();
    if (value === null) missing.push(key); else fields[key] = value;
  };
  const phone = suffixes => {
    const parts = suffixes.map(input);
    if (parts.some(x => x === null)) return null;
    if (parts.every(x => !x)) return '';
    if (parts.some(x => !x || !/^[0-9]+$/.test(x))) return null;
    return parts.join('-');
  };
  const choice = suffix => {
    const root = node(suffix), label = node(suffix + '_label');
    if (!root || root.getAttribute('role') !== 'combobox' || !label || !root.contains(label)) return null;
    const value = clean(label.textContent);
    return /^(선택|선택하세요|선택해 주세요|미선택)$/.test(value) ? '' : value;
  };
  const radio = heading => {
    // WebSquare's wq_uuid IDs change. Match a semantic heading and its own form group.
    const headings = [...document.querySelectorAll('[id^="' + prefix + '"]')]
      .filter(e => /^(H[1-6]|LABEL)$/.test(e.tagName) && clean(e.textContent).replace(/\s+/g, '').startsWith(heading));
    if (headings.length !== 1) return null;
    const group = headings[0].closest('.form-group');
    if (!group) return null;
    const checked = [...group.querySelectorAll('input[type="radio"]')].filter(e => e.checked);
    if (!checked.length) return '';
    if (checked.length !== 1) return null;
    const label = [...group.querySelectorAll('label')].find(e => e.htmlFor === checked[0].id);
    return label ? clean(label.textContent) : null;
  };
  const list = suffixes => {
    const roots = suffixes.map(node);
    if (roots.some(e => !e)) return null;
    const values = [];
    for (const root of roots) {
      if (!root.children.length) continue;
      // Only selected labels in the known disease/disability containers. Unknown row shapes
      // are omitted rather than interpreting arbitrary page text as a medical condition.
      for (const row of root.children) {
        const labels = [...row.querySelectorAll('.w2selectbox_label')];
        if (!labels.length || row.querySelector('input,textarea')) return null;
        const selected = labels.map(e => clean(e.textContent)).filter(x => x && !/^(선택|선택하세요|선택해 주세요|(?:질환|질병|장애유형|대분류|중분류|소분류)\s*선택)$/.test(x));
        if (selected.length) values.push(selected.join(' / '));
      }
    }
    if (values.length > 16) return null;
    return [...new Set(values)];
  };
  const guardians = () => {
    const body = node('grd_list_body_tbody');
    if (!body) return null;
    // col_id is present in the supplied grid. Do not rely on visual column order, hidden
    // internal columns, registration identifiers or the unfinished guardian editor below it.
    const columns = {name:'flnm', relation:'relNm', mobile:'mobAll', phone:'tlpAll', email:'eml', living:'habSeNm', smsPreference:'smsRcptnNm'};
    const rows = [];
    for (const row of body.rows) {
      if (row.classList.contains('w2grid_hidedRow') || getComputedStyle(row).display === 'none') continue;
      const value = key => {
        const cells = row.querySelectorAll('[col_id="' + columns[key] + '"]');
        if (cells.length !== 1) throw Error('STRUCTURE');
        return clean(cells[0].textContent);
      };
      const name = value('name');
      if (!name) continue;
      const g = {};
      for (const key of Object.keys(columns)) g[key] = value(key);
      rows.push(g);
    }
    return rows.length <= 8 ? rows : null;
  };
  try {
    currentField = 'name';
    const name = node('ibx_name');
    if (!name || !visible(name)) return fail('FORM_NOT_READY');
    currentField = '';
    if ([...document.querySelectorAll('[role="dialog"],.w2window')].some(visible)) return fail('DIALOG_OPEN');
    for (const [key, suffix] of Object.entries({
      name:'ibx_name', medicationHistory:'ibx_takngDrfstfCn',
      callUnavailableReason:'ibx_tlphonImposblRsn', pastHistory:'ibx_hstcsInfoCn',
      facilityName:'ibx_mdlcnstNm', discomfort:'ibx_incnvnncMttr', constitution:'ibx_excptnPhysgCn',
      responderRequests:'ibx_relifMemberDemandMttr', frequentPlace:'ibx_rcntVstRgnNm'
    })) put(key, () => input(suffix));
    put('registeredPhone', () => phone(['ibx_phone1','ibx_phone2','ibx_phone3']));
    if (!fields.name || !fields.registeredPhone || /[*●•]/.test(fields.name)) return fail('IDENTITY_NOT_READY');
    for (const [key, suffix] of Object.entries({applicationType:'sbx_propTp', applicationDetail:'sbx_propTpCode',
      mobility:'sbx_bhaveSeCode', bloodGroup:'sbx_bldtyCd', rhesus:'sbx_bldtySeCd', specialty:'sbx_mdlcnstMdexmSbjctCd'
    })) put(key, () => choice(suffix));
    put('dayPhone', () => phone(['ibx_mediInstTelNum1','ibx_mediInstTelNum2','ibx_mediInstTelNum3']));
    put('nightPhone', () => phone(['ibx_mediInstTelNumNight1','ibx_mediInstTelNumNight2','ibx_mediInstTelNumNight3']));
    put('elevator', () => radio('승강기설치여부'));
    put('canCall', () => radio('전화통화가능여부'));
    put('currentConditions', () => list(['gen_setDiss','gen_firstDiss']));
    put('disabilities', () => list(['gen_setFirstDsblty','gen_firstDsblty']));
    // Keep the displayed disease name intact: never guess medical delimiters/code mappings.
    put('infectiousDiseases', () => {
      const infection = input('ibx_contDissNm');
      return infection === null ? null : infection ? [infection] : [];
    });
    put('guardians', guardians);
    currentField = '';
    const result = JSON.stringify({schema:1, origin:location.origin, fields, missing});
    return result.length <= 32000 ? result : fail('TOO_LARGE');
  } catch (_) { return fail('UNSUPPORTED_FORM'); }
})();
