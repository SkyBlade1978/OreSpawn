package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceCandidate;
import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceGroup;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** Single-screen material-group editor with independently paginated panes. */
final class OreSourceListScreen extends OreSpawnScreen {
	private static final int HORIZONTAL_MARGIN = 10;
	private static final int PANE_GAP = 6;
	private static final int CONTENT_TOP = 28;
	private static final int ROW_HEIGHT = 22;
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final Map<String, TextFieldWidget> weights = new LinkedHashMap<>();
	private boolean advanced;
	private int groupPage;
	private int aliasPage;
	private int outputPage;
	private int channelIndex;
	private String selectedKey;
	private String pendingAliasMove;
	private String error;
	private TextFieldWidget nameField;
	private TextFieldWidget aliasField;
	private int leftPaneX;
	private int leftPaneWidth;
	private int rightPaneX;
	private int rightPaneWidth;
	private int paneBottom;
	private int groupNavigationY;
	private int groupPages = 1;
	private int nameLabelY;
	private int aliasLabelY;
	private int aliasPages = 1;
	private int outputNavigationY;
	private int outputPages = 1;

	OreSourceListScreen(GuiScreen parent, GeologyEditorSession session) {
		super(new TextComponentTranslation("screen.orespawn.ore_sources"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		weights.clear();
		nameField = null;
		aliasField = null;
		List<OreSourceGroup> groups = orderedGroups();
		if (selected(groups) == null && !groups.isEmpty()) selectedKey = groups.get(0).key;

		int contentWidth = contentWidth(width);
		leftPaneX = (width - contentWidth) / 2;
		leftPaneWidth = leftPaneWidth(width);
		rightPaneX = leftPaneX + leftPaneWidth + PANE_GAP;
		rightPaneWidth = contentWidth - leftPaneWidth - PANE_GAP;
		paneBottom = height - 32;

		initDirectory(leftPaneX, CONTENT_TOP, leftPaneWidth, groups);
		initOutputs(rightPaneX, CONTENT_TOP, rightPaneWidth);
		addButton(new Button(width / 2 - 75, height - 28, 150, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private void initDirectory(int left, int top, int paneWidth, List<OreSourceGroup> groups) {
		addButton(new Button(left + paneWidth - 24, top, 24, 20,
				new TextComponentString("+"), button -> {
					syncFields();
					selectedKey = session.addOreMaterialGroup();
					pendingAliasMove = null;
					aliasPage = 0;
					outputPage = 0;
					List<OreSourceGroup> updated = orderedGroups();
					int index = groupIndexOf(updated, selectedKey);
					groupPage = index < 0 ? 0 : index / groupRowCount(height);
					rebuild(false);
				}, (button, mouseX, mouseY) -> renderStringTooltip(
						java.util.Collections.singletonList(I18n.format(
								"button.orespawn.ore_source.add_group")), mouseX, mouseY)));

		int listTop = top + 22;
		int rows = groupRowCount(height);
		groupPages = pageCount(groups.size(), rows);
		groupPage = Math.max(0, Math.min(groupPage, groupPages - 1));
		int start = groupPage * rows;
		for (int index = 0; index < rows && start + index < groups.size(); index++) {
			OreSourceGroup group = groups.get(start + index);
			int y = listTop + (index * ROW_HEIGHT);
			String prefix = group.key.equals(selectedKey) ? "> " : group.needsAttention() ? "! " : "";
			String label = prefix + group.displayName + " - " + display(group.domain);
			addButton(new Button(left, y, paneWidth, 20,
					OreSpawnScreenLayout.fit(font, new TextComponentString(label), paneWidth - 8), button -> {
						syncFields();
						selectedKey = group.key;
						pendingAliasMove = null;
						aliasPage = 0;
						outputPage = 0;
						error = null;
						rebuild(false);
					}, (button, mouseX, mouseY) -> renderStringTooltip(groupTooltip(group), mouseX, mouseY)));
		}

		groupNavigationY = listTop + (rows * ROW_HEIGHT);
		Button previous = addButton(new Button(left, groupNavigationY, 32, 20,
				new TextComponentString("<"), button -> {
					syncFields();
					groupPage--;
					rebuild(false);
				}));
		Button next = addButton(new Button(left + paneWidth - 32, groupNavigationY, 32, 20,
				new TextComponentString(">"), button -> {
					syncFields();
					groupPage++;
					rebuild(false);
				}));
		previous.enabled = groupPage > 0;
		next.enabled = groupPage + 1 < groupPages;
		initGroupEditor(left, groupNavigationY + 24, paneWidth);
	}

	private void initGroupEditor(int left, int top, int paneWidth) {
		OreSourceGroup group = selected(orderedGroups());
		if (group == null) return;

		nameLabelY = top;
		int nameTop = top + 10;
		int actionWidth = Math.min(58, Math.max(41, paneWidth / 3));
		nameField = addButton(new TextFieldWidget(font, left, nameTop,
				paneWidth - actionWidth - 5, 20,
				new TextComponentTranslation("option.orespawn.ore_source.group_name")));
		nameField.setMaxLength(64);
		nameField.setValue(group.displayName);
		Button groupAction = addButton(OreSpawnScreenLayout.button(this, font,
				left + paneWidth - actionWidth, nameTop, actionWidth, 20,
				new TextComponentTranslation(group.curated ? "button.orespawn.reset"
						: "button.orespawn.ore_source.delete_group"), button -> {
					if (group.curated) session.resetOreMaterialGroup(group.material);
					else {
						session.deleteOreMaterialGroup(group.material);
						selectedKey = null;
						groupPage = 0;
					}
					pendingAliasMove = null;
					rebuild(false);
				}));
		OreSpawnScreenLayout.explain(this, groupAction, group.curated
				? "tooltip.orespawn.ore_source.reset_group" : "tooltip.orespawn.ore_source.delete_group");

		aliasLabelY = nameTop + 24;
		int aliasesTop = aliasLabelY + 10;
		int rows = aliasRowCount(height);
		List<String> aliases = group.oreDictionaryEntries;
		aliasPages = pageCount(aliases.size(), rows);
		aliasPage = Math.max(0, Math.min(aliasPage, aliasPages - 1));
		int start = aliasPage * rows;
		for (int index = 0; index < rows && start + index < aliases.size(); index++) {
			String alias = aliases.get(start + index);
			int y = aliasesTop + (index * ROW_HEIGHT);
			addButton(new Button(left, y, paneWidth - 27, 20,
					OreSpawnScreenLayout.fit(font, new TextComponentString(alias), paneWidth - 35),
					button -> { }, (button, mouseX, mouseY) -> renderStringTooltip(
							java.util.Collections.singletonList(alias), mouseX, mouseY)));
			addButton(new Button(left + paneWidth - 22, y, 22, 20,
					new TextComponentString("-"), button -> {
						syncName();
						session.removeOreMaterialAlias(group.material, alias);
						pendingAliasMove = null;
						rebuild(false);
					}));
		}

		int addTop = aliasesTop + (rows * ROW_HEIGHT) + 2;
		int addWidth = 41;
		int fieldWidth = Math.max(30, paneWidth - addWidth - 49);
		aliasField = addButton(new TextFieldWidget(font, left, addTop, fieldWidth, 20,
				new TextComponentTranslation("option.orespawn.ore_source.alias")));
		aliasField.setMaxLength(128);
		if (pendingAliasMove != null) {
			int split = pendingAliasMove.indexOf('|');
			aliasField.setValue(split < 0 ? pendingAliasMove : pendingAliasMove.substring(0, split));
		}
		Button aliasPrevious = addButton(new Button(left + fieldWidth + 3, addTop, 20, 20,
				new TextComponentString("<"), button -> {
					syncName();
					aliasPage--;
					rebuild(false);
				}));
		Button aliasNext = addButton(new Button(left + fieldWidth + 26, addTop, 20, 20,
				new TextComponentString(">"), button -> {
					syncName();
					aliasPage++;
					rebuild(false);
				}));
		aliasPrevious.enabled = aliasPage > 0;
		aliasNext.enabled = aliasPage + 1 < aliasPages;
		Button add = addButton(new Button(left + paneWidth - addWidth, addTop, addWidth, 20,
				new TextComponentString(pendingAliasMove == null ? "+"
						: I18n.format("button.orespawn.ore_source.move")), button -> addAlias(group)));
		OreSpawnScreenLayout.explain(this, add, "tooltip.orespawn.ore_source.alias");
	}

	private void initOutputs(int left, int top, int paneWidth) {
		OreSourceGroup group = selected(orderedGroups());
		if (group == null) return;
		String mode = "keep_separate".equals(group.mode) ? "keep_original" : group.outputMode;
		Button modeButton = addButton(OreSpawnScreenLayout.button(this, font, left, top, paneWidth, 20,
				new TextComponentTranslation("option.orespawn.ore_source.output_mode",
						new TextComponentTranslation("mode.orespawn.ore_source." + mode).getFormattedText()), button -> {
					syncFields();
					cycleMode(group);
					outputPage = 0;
					rebuild(false);
				}));
		OreSpawnScreenLayout.explain(this, modeButton, "tooltip.orespawn.ore_source.output_mode");

		int listTop = top + 24;
		List<OreSourceCandidate> candidates = group.outputCandidates();
		int rows = outputRowCount(height, advanced);
		outputPages = pageCount(candidates.size(), rows);
		outputPage = Math.max(0, Math.min(outputPage, outputPages - 1));
		int start = outputPage * rows;
		for (int index = 0; index < rows && start + index < candidates.size(); index++) {
			OreSourceCandidate candidate = candidates.get(start + index);
			int y = listTop + (index * ROW_HEIGHT);
			boolean selected = group.outputs.containsKey(candidate.sourceId);
			boolean single = "single".equals(group.outputMode) && !"keep_separate".equals(group.mode);
			String marker = single ? (selected ? "(*) " : "( ) ") : (selected ? "[x] " : "[ ] ");
			int weightWidth = "custom".equals(group.outputMode)
					&& !"keep_separate".equals(group.mode) ? 58 : 0;
			Button toggle = addButton(new Button(left, y,
					paneWidth - weightWidth - (weightWidth == 0 ? 0 : 5), 20,
					OreSpawnScreenLayout.fit(font, new TextComponentString(marker + candidateLabel(candidate)),
							paneWidth - weightWidth - 13), button -> {
						syncFields();
						session.setOreSourceOutput(group.key, candidate.sourceId,
								single || !group.outputs.containsKey(candidate.sourceId),
								group.outputs.containsKey(candidate.sourceId)
										? group.outputs.get(candidate.sourceId) : 1.0D);
						rebuild(false);
					}, (button, mouseX, mouseY) -> renderStringTooltip(candidateTooltip(candidate), mouseX, mouseY)));
			toggle.enabled = candidate.loaded && !candidate.enrichment && !"keep_separate".equals(group.mode);
			if (weightWidth > 0) {
				TextFieldWidget weight = addButton(new TextFieldWidget(font, left + paneWidth - weightWidth,
						y, weightWidth, 20,
						new TextComponentTranslation("option.orespawn.ore_source.weight")));
				weight.setMaxLength(12);
				weight.setValue(formatWeight(group.outputs.get(candidate.sourceId)));
				weight.enabled = selected && candidate.loaded && !candidate.enrichment;
				weights.put(candidate.sourceId, weight);
			}
		}

		outputNavigationY = listTop + (rows * ROW_HEIGHT);
		Button previous = addButton(new Button(left, outputNavigationY, 32, 20,
				new TextComponentString("<"), button -> {
					syncFields();
					outputPage--;
					rebuild(false);
				}));
		Button next = addButton(new Button(left + paneWidth - 32, outputNavigationY, 32, 20,
				new TextComponentString(">"), button -> {
					syncFields();
					outputPage++;
					rebuild(false);
				}));
		previous.enabled = outputPage > 0;
		next.enabled = outputPage + 1 < outputPages;
		addButton(OreSpawnScreenLayout.button(this, font, left + 37, outputNavigationY,
				paneWidth - 74, 20,
				new TextComponentTranslation(advanced ? "button.orespawn.ore_source.hide_advanced"
						: "button.orespawn.ore_source.advanced"), button -> {
					syncFields();
					advanced = !advanced;
					rebuild(false);
				}));
		if (advanced) initAdvanced(group, left, outputNavigationY + 24, paneWidth);
	}

	private void initAdvanced(OreSourceGroup group, int left, int y, int paneWidth) {
		List<String> channels = group.channels();
		if (channels.isEmpty()) return;
		channelIndex = Math.max(0, Math.min(channelIndex, channels.size() - 1));
		String channel = channels.get(channelIndex);
		int half = (paneWidth - 5) / 2;
		addButton(OreSpawnScreenLayout.button(this, font, left, y, half, 20,
				new TextComponentString(display(channel)), button -> {
					channelIndex = (channelIndex + 1) % channels.size();
					rebuild(false);
				}));
		List<OreSourceCandidate> placements = placementCandidates(group, channel);
		String selected = group.placements.get(channel);
		int index = indexOf(placements, selected);
		if (index < 0 && !placements.isEmpty()) index = 0;
		final int selectedIndex = index;
		Button placement = addButton(OreSpawnScreenLayout.button(this, font,
				left + half + 5, y, half, 20,
				new TextComponentString(index < 0 ? I18n.format("label.orespawn.ore_source.missing")
						: candidateLabel(placements.get(index))), button -> {
					if (!placements.isEmpty()) {
						OreSourceCandidate next = placements.get((selectedIndex + 1) % placements.size());
						session.setOreSourcePlacement(group.key, channel, next.sourceId);
						rebuild(false);
					}
				}));
		placement.enabled = !placements.isEmpty();
	}

	private void cycleMode(OreSourceGroup group) {
		if ("keep_separate".equals(group.mode)) session.setOreSourceOutputMode(group.key, "balanced");
		else if ("balanced".equals(group.outputMode)) session.setOreSourceOutputMode(group.key, "single");
		else if ("single".equals(group.outputMode)) session.setOreSourceOutputMode(group.key, "custom");
		else session.setOreSourceMode(group.key, false);
	}

	private void addAlias(OreSourceGroup group) {
		syncName();
		String alias = aliasField == null ? "" : aliasField.getValue().trim();
		String owner = session.oreDictionaryOwner(alias);
		String move = owner == null || owner.equals(group.material) ? null : alias + '|' + owner;
		if (move != null && !move.equals(pendingAliasMove)) {
			pendingAliasMove = move;
			error = I18n.format("error.orespawn.ore_source.alias_owned", display(owner));
			rebuild(false);
			return;
		}
		if (!session.addOreMaterialAlias(group.material, alias, move != null)) {
			error = I18n.format("error.orespawn.ore_source.alias");
			return;
		}
		pendingAliasMove = null;
		error = null;
		aliasPage = Math.max(0, pageCount(group.oreDictionaryEntries.size() + 1,
				aliasRowCount(height)) - 1);
		rebuild(false);
	}

	private void syncFields() {
		syncName();
		OreSourceGroup group = selected(orderedGroups());
		if (group == null || !"custom".equals(group.outputMode)) return;
		for (Map.Entry<String, TextFieldWidget> entry : weights.entrySet()) {
			if (!group.outputs.containsKey(entry.getKey())) continue;
			try {
				double value = Double.parseDouble(entry.getValue().getValue().trim());
				if (!Double.isFinite(value) || value <= 0.0D) throw new NumberFormatException();
				session.setOreSourceOutput(group.key, entry.getKey(), true, value);
			} catch (NumberFormatException invalid) {
				error = I18n.format("error.orespawn.ore_source.weight");
			}
		}
	}

	private void syncName() {
		OreSourceGroup group = selected(orderedGroups());
		if (group != null && nameField != null) {
			session.renameOreMaterialGroup(group.material, nameField.getValue());
		}
	}

	private List<OreSourceGroup> orderedGroups() {
		List<OreSourceGroup> result = new ArrayList<>(session.oreSourceGroups());
		result.sort((left, right) -> {
			if (left.needsAttention() != right.needsAttention()) return left.needsAttention() ? -1 : 1;
			int name = left.displayName.compareToIgnoreCase(right.displayName);
			if (name != 0) return name;
			int material = left.material.compareTo(right.material);
			return material != 0 ? material : left.domain.compareTo(right.domain);
		});
		return result;
	}

	private OreSourceGroup selected(List<OreSourceGroup> groups) {
		if (selectedKey == null) return null;
		for (OreSourceGroup group : groups) if (selectedKey.equals(group.key)) return group;
		return null;
	}

	private static List<OreSourceCandidate> placementCandidates(OreSourceGroup group, String channel) {
		List<OreSourceCandidate> result = new ArrayList<>();
		for (OreSourceCandidate candidate : group.candidates) {
			if (candidate.loaded && candidate.active && !candidate.external && !candidate.enrichment
					&& channel.equals(candidate.channel)) result.add(candidate);
		}
		return result;
	}

	private static int indexOf(List<OreSourceCandidate> candidates, String sourceId) {
		for (int index = 0; index < candidates.size(); index++) {
			if (candidates.get(index).sourceId.equals(sourceId)) return index;
		}
		return -1;
	}

	private static int groupIndexOf(List<OreSourceGroup> groups, String key) {
		for (int index = 0; index < groups.size(); index++) {
			if (groups.get(index).key.equals(key)) return index;
		}
		return -1;
	}

	private static String candidateLabel(OreSourceCandidate candidate) {
		String owner = candidate.ownerName.isEmpty() ? candidate.owner : candidate.ownerName;
		String state = candidate.external ? I18n.format("status.orespawn.ore_source.external_short")
				: !candidate.loaded ? I18n.format("status.orespawn.ore_source.missing_short")
				: !candidate.active ? I18n.format("status.orespawn.ore_source.output_only") : "";
		return owner + " - " + candidate.registryId + (state.isEmpty() ? "" : " (" + state + ")");
	}

	private static List<String> candidateTooltip(OreSourceCandidate candidate) {
		List<String> result = new ArrayList<>();
		result.add(I18n.format("label.orespawn.ore_source.owner", candidate.owner,
				candidate.ownerVersion.isEmpty() ? "?" : candidate.ownerVersion));
		result.add(I18n.format("label.orespawn.ore_source.registry", candidate.registryId, candidate.metadata));
		result.add(I18n.format("label.orespawn.ore_source.dictionary",
				candidate.oreDictionary.isEmpty() ? "-" : String.join(", ", candidate.oreDictionary)));
		result.add(I18n.format(candidate.external ? "tooltip.orespawn.ore_source.external"
				: !candidate.loaded ? "tooltip.orespawn.ore_source.missing"
				: !candidate.active ? "tooltip.orespawn.ore_source.output_only"
				: "tooltip.orespawn.ore_source.controlled"));
		return result;
	}

	private static List<String> groupTooltip(OreSourceGroup group) {
		List<String> result = new ArrayList<>();
		result.add(group.material);
		result.add(I18n.format("label.orespawn.ore_source.summary", group.outputCandidates().size(),
				I18n.format("status.orespawn.ore_source." + group.status)));
		result.add(group.oreDictionaryEntries.isEmpty() ? "-"
				: String.join(", ", group.oreDictionaryEntries));
		return result;
	}

	private static String formatWeight(Double weight) {
		if (weight == null) return "1";
		long whole = weight.longValue();
		return weight.doubleValue() == whole ? Long.toString(whole) : Double.toString(weight.doubleValue());
	}

	private static String display(String id) {
		int split = id.indexOf(':');
		String value = (split < 0 ? id : id.substring(split + 1)).replace('_', ' ').replace('/', ' ');
		return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private void rebuild(boolean clearError) {
		if (clearError) error = null;
		buttons.clear();
		children.clear();
		init();
	}

	static int contentWidth(int width) {
		return Math.max(240, Math.min(700, width - (HORIZONTAL_MARGIN * 2)));
	}

	static int leftPaneWidth(int width) {
		int content = contentWidth(width);
		return Math.max(104, Math.min(280, (content * 43) / 100));
	}

	static int aliasRowCount(int height) {
		return height >= 252 ? 2 : 1;
	}

	static int groupRowCount(int height) {
		int available = (height - 32) - CONTENT_TOP;
		int fixed = 112 + (aliasRowCount(height) * ROW_HEIGHT);
		return Math.max(1, Math.min(5, (available - fixed) / ROW_HEIGHT));
	}

	static int outputRowCount(int height, boolean advanced) {
		int available = (height - 32) - (CONTENT_TOP + 24) - (advanced ? 68 : 44);
		return Math.max(1, available / ROW_HEIGHT);
	}

	static int pageCount(int entries, int pageSize) {
		return Math.max(1, (Math.max(0, entries) + Math.max(1, pageSize) - 1) / Math.max(1, pageSize));
	}

	@Override
	public void onClose() {
		syncFields();
		if (error == null) minecraft.displayGuiScreen(parent);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawRect(leftPaneX - 2, CONTENT_TOP - 2,
				leftPaneX + leftPaneWidth + 2, paneBottom + 2, 0x70000000);
		drawRect(rightPaneX - 2, CONTENT_TOP - 2,
				rightPaneX + rightPaneWidth + 2, paneBottom + 2, 0x70000000);
		drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
		if (error != null) {
			drawCenteredString(font, new TextComponentString(OreSpawnScreenLayout.fit(font,
					new TextComponentString(error), width - 24)), width / 2, 19, 0xFF5555);
		}
		drawString(font, new TextComponentTranslation("button.orespawn.ore_source.all_groups"),
				leftPaneX + 4, CONTENT_TOP + 6, 0xFFFFFF);
		drawCenteredString(font, new TextComponentString((groupPage + 1) + " / " + groupPages),
				leftPaneX + (leftPaneWidth / 2), groupNavigationY + 6, 0xA0A0A0);
		OreSourceGroup group = selected(orderedGroups());
		if (group == null) {
			drawCenteredString(font, new TextComponentTranslation("label.orespawn.ore_source.none"),
					leftPaneX + (leftPaneWidth / 2), CONTENT_TOP + 68, 0xA0A0A0);
		} else {
			drawString(font, new TextComponentTranslation("option.orespawn.ore_source.group_name"),
					leftPaneX + 2, nameLabelY, 0xA0A0A0);
			String aliases = I18n.format("option.orespawn.ore_source.alias")
					+ " " + (aliasPage + 1) + " / " + aliasPages;
			drawString(font, new TextComponentString(OreSpawnScreenLayout.fit(font,
					new TextComponentString(aliases), leftPaneWidth - 4)),
					leftPaneX + 2, aliasLabelY, 0xA0A0A0);
			drawCenteredString(font, new TextComponentString((outputPage + 1) + " / " + outputPages),
					rightPaneX + (rightPaneWidth / 2), outputNavigationY + 6, 0xA0A0A0);
		}
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
