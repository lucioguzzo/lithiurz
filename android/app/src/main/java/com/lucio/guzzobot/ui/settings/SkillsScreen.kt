package com.lucio.guzzobot.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lucio.guzzobot.R
import com.lucio.guzzobot.data.skills.Skill
import com.lucio.guzzobot.data.skills.SkillCategory
import com.lucio.guzzobot.ui.chat.ChatViewModel

/**
 * Le competenze.
 *
 * Si sfogliano, si applicano alla conversazione aperta, si modificano — anche
 * quelle fornite con l'app, perché il testo giusto per un lavoro lo conosce
 * chi quel lavoro lo fa — e se ne scrivono di nuove.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onApplied: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val active by viewModel.activeSkill.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Skill?>(null) }
    var confirmDelete by remember { mutableStateOf<Skill?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                },
                title = {
                    Text(
                        text = stringResource(R.string.skills),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            editing = Skill(
                                id = viewModel.newSkillId(),
                                name = "",
                                description = "",
                                instructions = "",
                            )
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.skill_new),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 36.dp),
        ) {
            Text(
                text = stringResource(R.string.skills_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            active?.let { current ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.skill_active, current.name),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.applySkill(null) }) {
                        Text(stringResource(R.string.skill_remove))
                    }
                }
            }

            SkillCategory.entries.forEach { category ->
                val group = settings.skills.filter { it.category == category }
                if (group.isEmpty()) return@forEach
                SectionTitle(stringResource(label(category)))
                group.forEach { skill ->
                    SkillRow(
                        skill = skill,
                        applied = skill.id == active?.id,
                        onApply = {
                            viewModel.applySkill(skill.id)
                            onApplied()
                        },
                        onEdit = { editing = skill },
                        onToggle = { viewModel.saveSkill(skill.copy(enabled = it)) },
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
        }
    }

    editing?.let { skill ->
        SkillEditor(
            skill = skill,
            onSave = {
                viewModel.saveSkill(it)
                editing = null
            },
            onDelete = {
                confirmDelete = skill
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    confirmDelete?.let { skill ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(skill.name) },
            text = { Text(stringResource(R.string.skill_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSkill(skill.id)
                    confirmDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun SkillRow(
    skill: Skill,
    applied: Boolean,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onApply)
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SkillIcons.of(skill),
            contentDescription = null,
            tint = if (applied) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .padding(end = 14.dp)
                .size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (applied) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onBackground
                    },
                )
                if (applied) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Text(
                text = skill.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text(stringResource(R.string.skill_edit)) }
        Switch(checked = skill.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun SkillEditor(
    skill: Skill,
    onSave: (Skill) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(skill.name) }
    var description by remember { mutableStateOf(skill.description) }
    var instructions by remember { mutableStateOf(skill.instructions) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (skill.name.isBlank()) {
                    stringResource(R.string.skill_new)
                } else {
                    skill.name
                },
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.skill_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.skill_when)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text(stringResource(R.string.skill_instructions)) },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.skill_editor_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        skill.copy(
                            name = name.trim(),
                            description = description.trim(),
                            instructions = instructions.trim(),
                        ),
                    )
                },
                enabled = name.isNotBlank() && instructions.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private fun label(category: SkillCategory): Int = when (category) {
    SkillCategory.SCRITTURA -> R.string.skill_writing
    SkillCategory.STUDIO -> R.string.skill_study
    SkillCategory.CODICE -> R.string.skill_code
    SkillCategory.CASA -> R.string.skill_home
    SkillCategory.ANALISI -> R.string.skill_analysis
}
