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

/** Responsive master/detail editor for material groups and their output blocks. */
final class OreSourceListScreen extends OreSpawnScreen {
	private static final int WIDE_MINIMUM = 520;
	private static final int ROW_HEIGHT = 22;
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final Map<String, TextFieldWidget> weights = new LinkedHashMap<>();
	private boolean showAll;
	private boolean compactDetail;
	private boolean advanced;
	private int groupPage;
	private int outputPage;
	private int aliasIndex;
	private int channelIndex;
	private String selectedKey;
	private String pendingAliasMove;
	private String error;
	private TextFieldWidget nameField;
	private TextFieldWidget aliasField;

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
		List<OreSourceGroup> groups = visibleGroups();
		if (selected(groups) == null && !groups.isEmpty()) selectedKey = groups.get(0).key;
		boolean compact = compact(width);
		int footer = height - 28;
		if (compact && !compactDetail) {
			initDirectory(12, 30, width - 24, footer - 34, groups, true);
		} else if (compact) {
			initCompactDetail(12, 28, width - 24, footer - 32);
		} else {
			int contentWidth = Math.min(700, width - 20);
			int left = (width - contentWidth) / 2;
			int leftWidth = Math.max(190, (contentWidth * 2) / 5);
			initDirectory(left, 28, leftWidth, footer - 32, groups, false);
			initOutputs(left + leftWidth + 8, 28, contentWidth - leftWidth - 8, footer - 32);
		}
		if (compact && compactDetail) {
			addButton(new Button(12, footer, 92, 20,
					new TextComponentTranslation("button.orespawn.back"), button -> {
						syncFields(); compactDetail = false; rebuild(false);
					}));
			addButton(new Button(width - 162, footer, 150, 20,
					DialogTexts.GUI_DONE, button -> onClose()));
		} else {
			addButton(new Button(width / 2 - 75, footer, 150, 20,
					DialogTexts.GUI_DONE, button -> onClose()));
		}
	}

	private void initDirectory(int left, int top, int paneWidth, int bottom,
			List<OreSourceGroup> groups, boolean compactLayout) {
		int half = (paneWidth - 5) / 2;
		addButton(OreSpawnScreenLayout.button(this, font, left, top, half, 20,
				new TextComponentTranslation(showAll ? "button.orespawn.ore_source.needs_attention"
						: "button.orespawn.ore_source.all_groups"), button -> {
					showAll = !showAll; groupPage = 0; selectedKey = null; rebuild(true);
				}));
		addButton(OreSpawnScreenLayout.button(this, font, left + half + 5, top, half, 20,
				new TextComponentTranslation("button.orespawn.ore_source.add_group"), button -> {
					syncFields(); selectedKey = session.addOreMaterialGroup(); showAll = true;
					compactDetail = compact(width); groupPage = 0; rebuild(false);
				}));

		int listTop = top + 24;
		int editorReserve = compactLayout ? 24 : 94;
		int rows = Math.max(1, Math.min(5, (bottom - listTop - editorReserve - 24) / ROW_HEIGHT));
		int pages = pageCount(groups.size(), rows);
		groupPage = Math.max(0, Math.min(groupPage, pages - 1));
		int start = groupPage * rows;
		for (int index = 0; index < rows && start + index < groups.size(); index++) {
			OreSourceGroup group = groups.get(start + index);
			int y = listTop + (index * ROW_HEIGHT);
			String prefix = group.key.equals(selectedKey) ? "> " : "";
			String label = prefix + group.displayName + " - " + display(group.domain);
			Button row = addButton(new Button(left, y, paneWidth, 20,
					OreSpawnScreenLayout.fit(font, new TextComponentString(label), paneWidth - 8), button -> {
						syncFields(); selectedKey = group.key; compactDetail = compact(width);
						outputPage = 0; aliasIndex = 0; error = null; rebuild(false);
					}, (button, mouseX, mouseY) -> renderStringTooltip(groupTooltip(group), mouseX, mouseY)));
			row.enabled = !group.key.equals(selectedKey) || compactLayout;
		}
		int navigationY = listTop + (rows * ROW_HEIGHT);
		Button previous = addButton(new Button(left, navigationY, 48, 20,
				new TextComponentString("<"), button -> { syncFields(); groupPage--; rebuild(false); }));
		Button next = addButton(new Button(left + paneWidth - 48, navigationY, 48, 20,
				new TextComponentString(">"), button -> { syncFields(); groupPage++; rebuild(false); }));
		previous.enabled = groupPage > 0;
		next.enabled = groupPage + 1 < pages;
		if (!compactLayout) initGroupEditor(left, navigationY + 24, paneWidth, bottom);
	}

	private void initCompactDetail(int left, int top, int paneWidth, int bottom) {
		OreSourceGroup group = selected(session.oreSourceGroups());
		if (group == null) return;
		initGroupEditor(left, top, paneWidth, top + 70);
		initOutputs(left, top + 74, paneWidth, bottom);
	}

	private void initGroupEditor(int left, int top, int paneWidth, int bottom) {
		OreSourceGroup group = selected(session.oreSourceGroups());
		if (group == null || top + 20 > bottom) return;
		int actionWidth = 58;
		nameField = addButton(new TextFieldWidget(font, left, top, paneWidth - actionWidth - 5, 20,
				new TextComponentTranslation("option.orespawn.ore_source.group_name")));
		nameField.setMaxLength(64);
		nameField.setValue(group.displayName);
		Button groupAction = addButton(OreSpawnScreenLayout.button(this, font,
				left + paneWidth - actionWidth, top, actionWidth, 20,
				new TextComponentTranslation(group.curated ? "button.orespawn.reset"
						: "button.orespawn.ore_source.delete_group"), button -> {
					if (group.curated) session.resetOreMaterialGroup(group.material);
					else { session.deleteOreMaterialGroup(group.material); selectedKey = null; }
					rebuild(false);
				}));
		OreSpawnScreenLayout.explain(this, groupAction, group.curated
				? "tooltip.orespawn.ore_source.reset_group" : "tooltip.orespawn.ore_source.delete_group");
		if (top + 44 > bottom) return;
		List<String> aliases = group.oreDictionaryEntries;
		aliasIndex = aliases.isEmpty() ? 0 : Math.max(0, Math.min(aliasIndex, aliases.size() - 1));
		String alias = aliases.isEmpty() ? I18n.format("label.orespawn.ore_source.no_aliases")
				: aliases.get(aliasIndex);
		Button aliasButton = addButton(new Button(left, top + 24, paneWidth - 46, 20,
				OreSpawnScreenLayout.fit(font, new TextComponentString(alias), paneWidth - 54), button -> {
					if (!aliases.isEmpty()) { aliasIndex = (aliasIndex + 1) % aliases.size(); rebuild(false); }
				}));
		aliasButton.enabled = aliases.size() > 1;
		Button remove = addButton(new Button(left + paneWidth - 41, top + 24, 41, 20,
				new TextComponentString("-"), button -> {
					if (!aliases.isEmpty()) session.removeOreMaterialAlias(group.material, aliases.get(aliasIndex));
					aliasIndex = 0; rebuild(false);
				}));
		remove.enabled = !aliases.isEmpty();
		if (top + 68 > bottom) return;
		aliasField = addButton(new TextFieldWidget(font, left, top + 48, paneWidth - 46, 20,
				new TextComponentTranslation("option.orespawn.ore_source.alias")));
		aliasField.setMaxLength(128);
		if (pendingAliasMove != null) {
			int split = pendingAliasMove.indexOf('|');
			aliasField.setValue(split < 0 ? pendingAliasMove : pendingAliasMove.substring(0, split));
		}
		Button add = addButton(new Button(left + paneWidth - 41, top + 48, 41, 20,
				new TextComponentString(pendingAliasMove == null ? "+" : I18n.format("button.orespawn.ore_source.move")),
				button -> addAlias(group)));
		OreSpawnScreenLayout.explain(this, add, "tooltip.orespawn.ore_source.alias");
	}

	private void initOutputs(int left, int top, int paneWidth, int bottom) {
		OreSourceGroup group = selected(session.oreSourceGroups());
		if (group == null) return;
		String mode = "keep_separate".equals(group.mode) ? "keep_original" : group.outputMode;
		Button modeButton = addButton(OreSpawnScreenLayout.button(this, font, left, top, paneWidth, 20,
				new TextComponentTranslation("option.orespawn.ore_source.output_mode",
						new TextComponentTranslation("mode.orespawn.ore_source." + mode).getFormattedText()), button -> {
					syncFields(); cycleMode(group); rebuild(false);
				}));
		OreSpawnScreenLayout.explain(this, modeButton, "tooltip.orespawn.ore_source.output_mode");

		int advancedReserve = advanced ? 48 : 24;
		int listTop = top + 24;
		List<OreSourceCandidate> candidates = group.outputCandidates();
		int rows = Math.max(1, (bottom - listTop - advancedReserve - 22) / ROW_HEIGHT);
		int pages = pageCount(candidates.size(), rows);
		outputPage = Math.max(0, Math.min(outputPage, pages - 1));
		int start = outputPage * rows;
		for (int index = 0; index < rows && start + index < candidates.size(); index++) {
			OreSourceCandidate candidate = candidates.get(start + index);
			int y = listTop + (index * ROW_HEIGHT);
			boolean selected = group.outputs.containsKey(candidate.sourceId);
			int weightWidth = "custom".equals(group.outputMode) && !"keep_separate".equals(group.mode) ? 58 : 0;
			Button toggle = addButton(new Button(left, y, paneWidth - weightWidth - (weightWidth == 0 ? 0 : 5), 20,
					OreSpawnScreenLayout.fit(font, new TextComponentString((selected ? "[x] " : "[ ] ")
							+ candidateLabel(candidate)), paneWidth - weightWidth - 13), button -> {
						syncFields(); session.setOreSourceOutput(group.key, candidate.sourceId,
								!group.outputs.containsKey(candidate.sourceId),
								group.outputs.containsKey(candidate.sourceId) ? group.outputs.get(candidate.sourceId) : 1.0D);
						rebuild(false);
					}, (button, mouseX, mouseY) -> renderStringTooltip(candidateTooltip(candidate), mouseX, mouseY)));
			toggle.enabled = candidate.loaded && !candidate.enrichment && !"keep_separate".equals(group.mode);
			if (weightWidth > 0) {
				TextFieldWidget weight = addButton(new TextFieldWidget(font, left + paneWidth - weightWidth,
						y, weightWidth, 20, new TextComponentTranslation("option.orespawn.ore_source.weight")));
				weight.setMaxLength(12);
				weight.setValue(formatWeight(group.outputs.get(candidate.sourceId)));
				weight.enabled = selected && candidate.loaded && !candidate.enrichment;
				weights.put(candidate.sourceId, weight);
			}
		}
		int navigationY = listTop + (rows * ROW_HEIGHT);
		Button previous = addButton(new Button(left, navigationY, 40, 20,
				new TextComponentString("<"), button -> { syncFields(); outputPage--; rebuild(false); }));
		Button next = addButton(new Button(left + paneWidth - 40, navigationY, 40, 20,
				new TextComponentString(">"), button -> { syncFields(); outputPage++; rebuild(false); }));
		previous.enabled = outputPage > 0;
		next.enabled = outputPage + 1 < pages;
		Button advancedButton = addButton(OreSpawnScreenLayout.button(this, font,
				left + 45, navigationY, paneWidth - 90, 20,
				new TextComponentTranslation(advanced ? "button.orespawn.ore_source.hide_advanced"
						: "button.orespawn.ore_source.advanced"), button -> {
						syncFields(); advanced = !advanced; rebuild(false);
					}));
		if (advanced) initAdvanced(group, left, navigationY + 24, paneWidth);
	}

	private void initAdvanced(OreSourceGroup group, int left, int y, int paneWidth) {
		List<String> channels = group.channels();
		if (channels.isEmpty()) return;
		channelIndex = Math.max(0, Math.min(channelIndex, channels.size() - 1));
		String channel = channels.get(channelIndex);
		int half = (paneWidth - 5) / 2;
		addButton(OreSpawnScreenLayout.button(this, font, left, y, half, 20,
				new TextComponentString(display(channel)), button -> {
					channelIndex = (channelIndex + 1) % channels.size(); rebuild(false);
				}));
		List<OreSourceCandidate> placements = placementCandidates(group, channel);
		String selected = group.placements.get(channel);
		int index = indexOf(placements, selected);
		if (index < 0 && !placements.isEmpty()) index = 0;
		final int selectedIndex = index;
		Button placement = addButton(OreSpawnScreenLayout.button(this, font, left + half + 5, y, half, 20,
				new TextComponentString(index < 0 ? I18n.format("label.orespawn.ore_source.missing")
						: candidateLabel(placements.get(index))), button -> {
					if (!placements.isEmpty()) {
						OreSourceCandidate next = placements.get((selectedIndex + 1) % placements.size());
						session.setOreSourcePlacement(group.key, channel, next.sourceId); rebuild(false);
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
		aliasIndex = 0;
		rebuild(false);
	}

	private void syncFields() {
		syncName();
		OreSourceGroup group = selected(session.oreSourceGroups());
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
		OreSourceGroup group = selected(session.oreSourceGroups());
		if (group != null && nameField != null) session.renameOreMaterialGroup(group.material, nameField.getValue());
	}

	private List<OreSourceGroup> visibleGroups() {
		List<OreSourceGroup> all = session.oreSourceGroups();
		if (showAll) return all;
		List<OreSourceGroup> result = new ArrayList<>();
		for (OreSourceGroup group : all) if (group.needsAttention()) result.add(group);
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
		result.add(group.oreDictionaryEntries.isEmpty() ? "-" : String.join(", ", group.oreDictionaryEntries));
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

	static boolean compact(int width) { return width < WIDE_MINIMUM; }

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
		drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
		if (error != null) drawCenteredString(font, new TextComponentString(
				OreSpawnScreenLayout.fit(font, new TextComponentString(error), width - 24)), width / 2, 19, 0xFF5555);
		List<OreSourceGroup> groups = visibleGroups();
		if (groups.isEmpty()) drawCenteredString(font,
				new TextComponentTranslation(showAll ? "label.orespawn.ore_source.none"
						: "label.orespawn.ore_source.none_attention"), width / 2, height / 2, 0xA0A0A0);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
