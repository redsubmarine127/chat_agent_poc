export function normalizeSkillSelection(skills, loadedSkillIds, selectedSkillId) {
  const skillIds = new Set(skills.map((skill) => skill.id));
  const normalizedLoadedSkillIds = uniqueIds(loadedSkillIds).filter((skillId) => skillIds.has(skillId));
  const normalizedSelectedSkillId = normalizedLoadedSkillIds.includes(selectedSkillId)
    ? selectedSkillId
    : normalizedLoadedSkillIds[0] || '';

  return {
    loadedSkillIds: normalizedLoadedSkillIds,
    selectedSkillId: normalizedSelectedSkillId
  };
}

export function toggleSkillLoad(skills, loadedSkillIds, selectedSkillId, skillId, checked) {
  if (checked) {
    return normalizeSkillSelection(
      skills,
      [...loadedSkillIds, skillId],
      skillId
    );
  }

  return normalizeSkillSelection(
    skills,
    loadedSkillIds.filter((loadedSkillId) => loadedSkillId !== skillId),
    selectedSkillId === skillId ? '' : selectedSkillId
  );
}

export function selectLoadedSkill(skills, loadedSkillIds, selectedSkillId, skillId) {
  if (!loadedSkillIds.includes(skillId)) {
    return normalizeSkillSelection(skills, loadedSkillIds, selectedSkillId);
  }
  return normalizeSkillSelection(skills, loadedSkillIds, skillId);
}

function uniqueIds(values) {
  return [...new Set(values.filter(Boolean))];
}
