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

/** Edits one material/domain policy inside the pending world-settings session. */
final class OreSourceDetailScreen extends OreSpawnScreen {
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private final String groupKey;
	private final Map<String, TextFieldWidget> weights = new LinkedHashMap<>();
	private int channelIndex;
	private int page;
	private int pageSize;
	private String error;

	OreSourceDetailScreen(GuiScreen parent, GeologyEditorSession session, String groupKey) {
		super(new TextComponentTranslation("screen.orespawn.ore_source_detail"));
		this.parent = parent;
		this.session = session;
		this.groupKey = groupKey;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		weights.clear();
		OreSourceGroup group = group();
		if (group == null) {
			addButton(new Button(width / 2 - 75, height - 28, 150, 20,
					DialogTexts.GUI_DONE, button -> onClose()));
			return;
		}
		int contentWidth = Math.min(390, Math.max(240, width - 24));
		int left = (width - contentWidth) / 2;
		int half = (contentWidth - 5) / 2;
		Button mode = addButton(OreSpawnScreenLayout.button(this, font, left, 34, half, 20,
				new TextComponentTranslation("option.orespawn.ore_source.mode",
						new TextComponentTranslation("mode.orespawn.ore_source." + group.mode).getFormattedText()),
				button -> {
					syncWeights();
					session.setOreSourceMode(groupKey, !"consolidated".equals(group().mode));
					rebuild();
				}));
		OreSpawnScreenLayout.explain(this, mode, "tooltip.orespawn.ore_source.mode");

		List<String> channels = group.channels();
		if (channels.isEmpty()) channels = java.util.Collections.singletonList("orespawn:standard");
		channelIndex = Math.max(0, Math.min(channelIndex, channels.size() - 1));
		final List<String> channelValues = channels;
		Button channel = addButton(OreSpawnScreenLayout.button(this, font, left + half + 5, 34, half, 20,
				new TextComponentTranslation("option.orespawn.ore_source.channel",
						display(channelValues.get(channelIndex))), button -> {
						syncWeights();
						channelIndex = (channelIndex + 1) % channelValues.size();
						rebuild();
					}));
		OreSpawnScreenLayout.explain(this, channel, "tooltip.orespawn.ore_source.channel");

		String selectedChannel = channelValues.get(channelIndex);
		List<OreSourceCandidate> placementCandidates = candidatesForChannel(group, selectedChannel);
		String selected = group.placements.get(selectedChannel);
		OreSourceCandidate selectedCandidate = candidate(group.candidates, selected);
		int selectedIndex = indexOf(placementCandidates, selected);
		if (selectedIndex < 0 && !placementCandidates.isEmpty()) selectedIndex = 0;
		final int currentSelection = selectedIndex;
		Button placement = addButton(OreSpawnScreenLayout.button(this, font, left, 58, contentWidth, 20,
				new TextComponentTranslation("option.orespawn.ore_source.placement",
						selectedCandidate != null && !selectedCandidate.loaded
								? candidateLabel(selectedCandidate)
								: selectedIndex < 0 ? I18n.format("label.orespawn.ore_source.missing")
										: candidateLabel(placementCandidates.get(selectedIndex))), button -> {
						syncWeights();
						if (!placementCandidates.isEmpty()) {
							OreSourceCandidate next = placementCandidates.get((currentSelection + 1)
									% placementCandidates.size());
							session.setOreSourcePlacement(groupKey, selectedChannel, next.sourceId);
							rebuild();
						}
					}));
		placement.enabled = !placementCandidates.isEmpty();
		OreSpawnScreenLayout.explain(this, placement, "tooltip.orespawn.ore_source.placement");

		int listTop = 86;
		int controlsY = height - 52;
		pageSize = Math.max(1, (controlsY - listTop) / 24);
		int pages = OreSourceListScreen.pageCount(group.candidates.size(), pageSize);
		page = Math.max(0, Math.min(page, pages - 1));
		int start = page * pageSize;
		for (int index = 0; index < pageSize && start + index < group.candidates.size(); index++) {
			OreSourceCandidate candidate = group.candidates.get(start + index);
			int y = listTop + index * 24;
			boolean enabled = group.outputs.containsKey(candidate.sourceId);
			Button toggle = addButton(new Button(left, y, contentWidth - 92, 20,
					OreSpawnScreenLayout.fit(font, new TextComponentString(
							(enabled ? "[x] " : "[ ] ") + candidateLabel(candidate)), contentWidth - 100),
					button -> {
						syncWeights();
						double current = group().outputs.containsKey(candidate.sourceId)
								? group().outputs.get(candidate.sourceId) : 1.0D;
						session.setOreSourceOutput(groupKey, candidate.sourceId,
								!group().outputs.containsKey(candidate.sourceId), current);
						rebuild();
					}, (button, mouseX, mouseY) -> renderStringTooltip(candidateTooltip(candidate), mouseX, mouseY)));
			toggle.enabled = !candidate.external && !candidate.enrichment && candidate.loaded;
			TextFieldWidget weight = addButton(new TextFieldWidget(font, left + contentWidth - 87, y, 87, 20,
					new TextComponentTranslation("option.orespawn.ore_source.weight")));
			weight.setMaxLength(12);
			weight.setValue(formatWeight(group.outputs.get(candidate.sourceId)));
			weight.enabled = enabled && !candidate.external && !candidate.enrichment && candidate.loaded;
			weights.put(candidate.sourceId, weight);
			OreSpawnScreenLayout.explain(this, weight, "tooltip.orespawn.ore_source.weight");
		}

		Button previous = addButton(new Button(left, controlsY, 60, 20, new TextComponentString("<"),
				button -> { syncWeights(); page--; rebuild(); }));
		Button next = addButton(new Button(left + contentWidth - 60, controlsY, 60, 20,
				new TextComponentString(">"), button -> { syncWeights(); page++; rebuild(); }));
		previous.enabled = page > 0;
		next.enabled = page + 1 < pages;
		addButton(new Button(width / 2 - 75, height - 28, 150, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private void syncWeights() {
		OreSourceGroup group = group();
		if (group == null) return;
		error = null;
		for (Map.Entry<String, TextFieldWidget> entry : weights.entrySet()) {
			if (!group.outputs.containsKey(entry.getKey())) continue;
			try {
				double value = Double.parseDouble(entry.getValue().getValue().trim());
				if (!Double.isFinite(value) || value <= 0.0D) throw new NumberFormatException();
				session.setOreSourceOutput(groupKey, entry.getKey(), true, value);
			} catch (NumberFormatException invalid) {
				error = I18n.format("error.orespawn.ore_source.weight");
			}
		}
	}

	private OreSourceGroup group() {
		for (OreSourceGroup value : session.oreSourceGroups()) if (groupKey.equals(value.key)) return value;
		return null;
	}

	private static List<OreSourceCandidate> candidatesForChannel(OreSourceGroup group, String channel) {
		List<OreSourceCandidate> result = new ArrayList<>();
		for (OreSourceCandidate candidate : group.candidates) {
			if (!candidate.external && !candidate.enrichment && candidate.loaded
					&& channel.equals(candidate.channel)) result.add(candidate);
		}
		return result;
	}

	private static OreSourceCandidate candidate(List<OreSourceCandidate> candidates, String sourceId) {
		if (sourceId == null) return null;
		for (OreSourceCandidate candidate : candidates) {
			if (sourceId.equals(candidate.sourceId)) return candidate;
		}
		return null;
	}

	private static int indexOf(List<OreSourceCandidate> candidates, String sourceId) {
		for (int index = 0; index < candidates.size(); index++) {
			if (candidates.get(index).sourceId.equals(sourceId)) return index;
		}
		return -1;
	}

	private static String candidateLabel(OreSourceCandidate candidate) {
		String owner = candidate.ownerName.isEmpty() ? candidate.owner : candidate.ownerName;
		return owner + " - " + candidate.registryId + (candidate.loaded ? ""
				: " (" + I18n.format("status.orespawn.ore_source.missing_source") + ")");
	}

	private static List<String> candidateTooltip(OreSourceCandidate candidate) {
		List<String> result = new ArrayList<>();
		result.add(I18n.format("label.orespawn.ore_source.owner", candidate.owner,
				candidate.ownerVersion.isEmpty() ? "?" : candidate.ownerVersion));
		result.add(I18n.format("label.orespawn.ore_source.registry", candidate.registryId,
				candidate.metadata));
		result.add(I18n.format("label.orespawn.ore_source.dictionary",
				candidate.oreDictionary.isEmpty() ? "-" : String.join(", ", candidate.oreDictionary)));
		result.add(I18n.format(candidate.external ? "tooltip.orespawn.ore_source.external"
				: candidate.enrichment ? "tooltip.orespawn.ore_source.enrichment"
				: candidate.loaded ? "tooltip.orespawn.ore_source.controlled"
						: "tooltip.orespawn.ore_source.missing"));
		return result;
	}

	private static String formatWeight(Double weight) {
		if (weight == null) return "1";
		long whole = weight.longValue();
		return weight.doubleValue() == whole ? Long.toString(whole) : Double.toString(weight.doubleValue());
	}

	private static String display(String id) {
		int split = id.indexOf(':');
		return (split < 0 ? id : id.substring(split + 1)).replace('_', ' ');
	}

	private void rebuild() { buttons.clear(); children.clear(); init(); }

	@Override
	public void onClose() {
		syncWeights();
		if (error == null) minecraft.displayGuiScreen(parent);
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		OreSourceGroup group = group();
		drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
		if (group != null) drawCenteredString(font,
				new TextComponentString(display(group.material) + " - " + display(group.domain)),
				width / 2, 20, 0xA0A0A0);
		if (error != null) drawCenteredString(font, new TextComponentString(error), width / 2,
				height - 64, 0xFF5555);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
