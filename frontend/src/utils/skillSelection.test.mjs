import assert from 'node:assert/strict';
import test from 'node:test';

import {
  normalizeSkillSelection,
  selectLoadedSkill,
  toggleSkillLoad
} from './skillSelection.js';

const skills = [
  { id: 'general', name: '通用助手' },
  { id: 'code-review', name: '代码审查' }
];

test('normalizes selection to empty when no skill is loaded', () => {
  const state = normalizeSkillSelection(skills, [], 'general');

  assert.deepEqual(state, {
    loadedSkillIds: [],
    selectedSkillId: ''
  });
});

test('selecting an unloaded skill should not load or select it', () => {
  const state = selectLoadedSkill(skills, [], '', 'code-review');

  assert.deepEqual(state, {
    loadedSkillIds: [],
    selectedSkillId: ''
  });
});

test('checking a skill loads and selects it', () => {
  const state = toggleSkillLoad(skills, [], '', 'code-review', true);

  assert.deepEqual(state, {
    loadedSkillIds: ['code-review'],
    selectedSkillId: 'code-review'
  });
});

test('unchecking the selected skill selects the next loaded skill', () => {
  const state = toggleSkillLoad(skills, ['general', 'code-review'], 'code-review', 'code-review', false);

  assert.deepEqual(state, {
    loadedSkillIds: ['general'],
    selectedSkillId: 'general'
  });
});
