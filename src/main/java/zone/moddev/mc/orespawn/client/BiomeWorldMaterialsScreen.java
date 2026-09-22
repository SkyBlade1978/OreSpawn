package zone.moddev.mc.orespawn.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Mouse;

import zone.moddev.mc.orespawn.client.BiomeDirectoryModel.BiomeEntry;
import zone.moddev.mc.orespawn.client.BiomeDirectoryModel.Placement;
import zone.moddev.mc.orespawn.client.BiomeDirectoryModel.Snapshot;
import zone.moddev.mc.orespawn.client.BiomeDirectoryModel.Status;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** Responsive directory for biome placement, exact replacements, and diagnostics. */
final class BiomeWorldMaterialsScreen extends OreSpawnScreen {
	private static final int TWO_PANE_MINIMUM = 400;
	private static final int CONTENT_TOP = 48;
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private String dimension;
	private String selectedId;
	private boolean showAll;
	private boolean compactDetail;
	private boolean resetArmed;
	private String message;
	private int biomeScroll;
	private int ruleScroll;
	private CompactScrollList biomeList;
	private CompactScrollList ruleList;
	private int detailX;
	private int detailWidth;
	private int detailRuleTop;

	BiomeWorldMaterialsScreen(GuiScreen parent, GeologyEditorSession session) {
		super(new TextComponentTranslation("screen.orespawn.biomes"));
		this.parent = parent;
		this.session = session;
		this.dimension = session.availableDimensionIds().get(0);
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		Snapshot snapshot = session.biomeDirectory();
		List<String> dimensions = new ArrayList<>(snapshot.dimensions);
		if (dimensions.isEmpty()) dimensions.add("minecraft:overworld");
		if (!dimensions.contains(dimension)) dimension = dimensions.get(0);
		List<BiomeEntry> entries = snapshot.entries(dimension, showAll);
		if (selectedId == null || find(entries, selectedId) == null) {
			selectedId = entries.isEmpty() ? null : entries.get(0).id;
		}
		boolean compact = width < TWO_PANE_MINIMUM;
		if (compact && compactDetail && selectedId != null) initDetail(snapshot, true);
		else initDirectory(snapshot, entries, compact);
	}

	private void initDirectory(Snapshot snapshot, List<BiomeEntry> entries, boolean compact) {
		int contentWidth = contentWidth(width);
		int left = (width - contentWidth) / 2;
		int leftWidth = compact ? contentWidth : leftPaneWidth(width);
		int headerWidth = Math.max(76, Math.min(leftWidth - 74, 150));
		addButton(OreSpawnScreenLayout.button(this, font, left, 24, headerWidth, 20,
				new TextComponentString(dimension), button -> cycleDimension(snapshot)));
		addButton(OreSpawnScreenLayout.button(this, font, left + leftWidth - 68, 24, 68, 20,
				new TextComponentTranslation(showAll ? "button.orespawn.biome.hide_routine"
						: "button.orespawn.show_all"), button -> {
					showAll = !showAll; selectedId = null; biomeScroll = 0; rebuild();
				}));
		int listHeight = listHeight(height);
		biomeList = addButton(new CompactScrollList(this, left, CONTENT_TOP, leftWidth, listHeight) {
			@Override protected int size() { return entries.size(); }
			@Override protected String rowText(int index) {
				BiomeEntry entry = entries.get(index);
				return statusMarker(entry.status) + ' ' + entry.name;
			}
			@Override protected int rowColor(int index) { return statusColor(entries.get(index).status); }
			@Override protected boolean rowSelected(int index) { return entries.get(index).id.equals(selectedId); }
			@Override protected void onRowPressed(int index) {
				selectedId = entries.get(index).id;
				if (compact) compactDetail = true;
				ruleScroll = 0; resetArmed = false; message = null; rebuild();
			}
			@Override protected List<String> rowTooltip(int index) { return biomeTooltip(entries.get(index)); }
		});
		biomeList.setFirstIndex(biomeScroll);
		if (!compact) initDetail(snapshot, false);
		addButton(new Button(width / 2 - 75, height - 28, 150, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private void initDetail(Snapshot snapshot, boolean compact) {
		BiomeEntry selected = find(snapshot.entries(dimension, true), selectedId);
		if (selected == null) return;
		int contentWidth = contentWidth(width);
		int contentLeft = (width - contentWidth) / 2;
		int leftWidth = compact ? 0 : leftPaneWidth(width);
		int x = compact ? contentLeft : contentLeft + leftWidth + 10;
		int paneWidth = compact ? contentWidth : contentWidth - leftWidth - 10;
		detailX = x;
		detailWidth = paneWidth;
		int y = compact ? 34 : CONTENT_TOP;
		if (compact) {
			addButton(OreSpawnScreenLayout.button(this, font, contentLeft, 8, 48, 20,
					new TextComponentTranslation("button.orespawn.back"), button -> {
						captureScroll(); compactDetail = false; rebuild();
					}));
		}
		ITextComponent replacement = selected.replacementTarget == null
				? new TextComponentTranslation("button.orespawn.biome.replace_new_terrain")
				: new TextComponentTranslation("button.orespawn.biome.replace_with", selected.replacementTarget);
		addButton(OreSpawnScreenLayout.explainedButton(this, font, x, y + 35,
				paneWidth, 20, replacement, button -> chooseReplacement(selected),
				"tooltip.orespawn.biome.no_retrogen"));
		if (selected.replacementTarget != null) {
			addButton(OreSpawnScreenLayout.explainedButton(this, font, x, y + 59,
					paneWidth, 20,
					new TextComponentTranslation("button.orespawn.biome.leave_original"),
					button -> { session.leaveBiomeOriginal(dimension, selected.id); message = null; rebuild(); },
					"tooltip.orespawn.biome.no_retrogen"));
		}
		int ruleTop = y + (selected.replacementTarget == null ? 61 : 85);
		detailRuleTop = ruleTop;
		int controlsTop = Math.max(ruleTop + 34, height - 76);
		int ruleHeight = Math.max(16, controlsTop - ruleTop - 4);
		List<Placement> placements = selected.placements;
		ruleList = addButton(new CompactScrollList(this, x, ruleTop, paneWidth, ruleHeight) {
			@Override protected int size() { return placements.size(); }
			@Override protected String rowText(int index) {
				Placement rule = placements.get(index);
				return (rule.enabled ? "[x] " : "[ ] ") + rule.paletteId + "  #" + (rule.order + 1)
						+ (rule.effectiveSurface ? "  *" : "");
			}
			@Override protected int rowColor(int index) { return placements.get(index).enabled ? 0xFFFFFF : 0x888888; }
			@Override protected void onRowPressed(int index) {
				Placement rule = placements.get(index);
				minecraft.displayGuiScreen(new BiomePlacementScreen(BiomeWorldMaterialsScreen.this,
						session, dimension, rule.paletteId, selected.id));
			}
			@Override protected List<String> rowTooltip(int index) { return placementTooltip(placements.get(index)); }
		});
		ruleList.setFirstIndex(ruleScroll);
		int half = (paneWidth - 4) / 2;
		addButton(OreSpawnScreenLayout.button(this, font, x, controlsTop, half, 20,
				new TextComponentTranslation("button.orespawn.biome.palettes", snapshot.palettes(dimension).size()),
				button -> minecraft.displayGuiScreen(new BiomePaletteScreen(this, session, dimension))));
		addButton(OreSpawnScreenLayout.button(this, font, x + half + 4, controlsTop, half, 20,
				new TextComponentTranslation("button.orespawn.dimension_materials"),
				button -> minecraft.displayGuiScreen(new DimensionMaterialsScreen(this, session, dimension))));
		addButton(OreSpawnScreenLayout.button(this, font, x, controlsTop + 24, half, 20,
				new TextComponentTranslation("button.orespawn.geome_influences"),
				button -> minecraft.displayGuiScreen(new GeomeBiomeScreen(this, session))));
		addButton(OreSpawnScreenLayout.button(this, font, x + half + 4, controlsTop + 24, half, 20,
				new TextComponentTranslation(resetArmed ? "button.orespawn.biome.confirm_reset"
						: "button.orespawn.biome.reset_selected"), button -> resetSelected(selected)));
	}

	private void chooseReplacement(BiomeEntry selected) {
		captureScroll();
		minecraft.displayGuiScreen(new BiomePickerScreen(this, session, target -> {
			try {
				session.replaceBiome(dimension, selected.id, target);
				message = session.biomeTargetProviderDeclared(dimension, target) ? null
						: I18n.format("warning.orespawn.biome.target_not_declared", target);
			} catch (IllegalArgumentException failure) {
				message = failure.getMessage();
			}
			minecraft.displayGuiScreen(this);
		}));
	}

	private void resetSelected(BiomeEntry selected) {
		if (!resetArmed) { resetArmed = true; rebuild(); return; }
		session.resetBiome(dimension, selected.id);
		resetArmed = false; message = null; rebuild();
	}

	private void cycleDimension(Snapshot snapshot) {
		List<String> dimensions = new ArrayList<>(snapshot.dimensions);
		int index = dimensions.indexOf(dimension);
		dimension = dimensions.get((Math.max(0, index) + 1) % dimensions.size());
		selectedId = null; compactDetail = false; biomeScroll = 0; ruleScroll = 0; rebuild();
	}

	private static BiomeEntry find(List<BiomeEntry> entries, String id) {
		if (id != null) for (BiomeEntry entry : entries) if (id.equals(entry.id)) return entry;
		return null;
	}

	private static String statusMarker(Status status) {
		switch (status) {
		case USER_REPLACED: return "R";
		case LAYERED: return "!";
		case DISABLED: return "-";
		case MISSING: return "?";
		case PROVIDER_MANAGED: return "+";
		default: return " ";
		}
	}

	private static int statusColor(Status status) {
		switch (status) {
		case MISSING: return 0xFF5555;
		case USER_REPLACED: return 0x55FFFF;
		case LAYERED: return 0xFFFF55;
		case DISABLED: return 0xAAAAAA;
		case PROVIDER_MANAGED: return 0x55FF55;
		default: return 0xFFFFFF;
		}
	}

	private static List<String> biomeTooltip(BiomeEntry entry) {
		List<String> lines = new ArrayList<>();
		lines.add(entry.name + " (" + entry.id + ")");
		lines.add(entry.ownerName + " " + entry.ownerVersion);
		lines.add(I18n.format("value.orespawn.biome.status." + entry.status.name().toLowerCase(java.util.Locale.ROOT)));
		lines.add(I18n.format("label.orespawn.biome.rule_count", entry.placements.size()));
		if (entry.replacementTarget != null) lines.add(I18n.format("label.orespawn.biome.replaced_by", entry.replacementTarget));
		return lines;
	}

	private static List<String> placementTooltip(Placement rule) {
		List<String> lines = new ArrayList<>();
		lines.add(rule.paletteId);
		lines.add(I18n.format("label.orespawn.biome.palette_owner_order", rule.owner, rule.order + 1));
		if (rule.hasSurface) lines.add(I18n.format(rule.effectiveSurface
				? "label.orespawn.biome.surface_effective" : "label.orespawn.biome.surface_overridden"));
		return lines;
	}

	private void captureScroll() {
		if (biomeList != null) biomeScroll = biomeList.firstIndex();
		if (ruleList != null) ruleScroll = ruleList.firstIndex();
	}

	private void rebuild() { captureScroll(); buttons.clear(); children.clear(); init(); }

	@Override public void onClose() { minecraft.displayGuiScreen(parent); }

	@Override
	public void handleMouseInput() throws java.io.IOException {
		super.handleMouseInput();
		int wheel = Mouse.getEventDWheel();
		if (wheel == 0) return;
		int mouseX = Mouse.getEventX() * width / minecraft.displayWidth;
		int mouseY = height - Mouse.getEventY() * height / minecraft.displayHeight - 1;
		if (biomeList != null && biomeList.mouseWheel(mouseX, mouseY, wheel)) biomeScroll = biomeList.firstIndex();
		else if (ruleList != null && ruleList.mouseWheel(mouseX, mouseY, wheel)) ruleScroll = ruleList.firstIndex();
	}

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 9, 0xFFFFFF);
		BiomeEntry selected = find(session.biomeDirectory().entries(dimension, true), selectedId);
		if (selected != null && (width >= TWO_PANE_MINIMUM || compactDetail)) {
			int contentWidth = contentWidth(width);
			int x = width < TWO_PANE_MINIMUM ? (width - contentWidth) / 2
					: (width - contentWidth) / 2 + leftPaneWidth(width) + 10;
			int y = width < TWO_PANE_MINIMUM ? 34 : CONTENT_TOP;
			drawString(font, new TextComponentString(selected.name), x + 3, y, 0xFFFFFF);
			drawString(font, new TextComponentString(selected.id), x + 3, y + 11, 0xAAAAAA);
				drawString(font, new TextComponentTranslation("value.orespawn.biome.status."
					+ selected.status.name().toLowerCase(java.util.Locale.ROOT)), x + 3, y + 22,
					statusColor(selected.status));
			if (selected.replacementTarget != null) {
				String notice = I18n.format("label.orespawn.biome.new_terrain_only");
				drawString(font, new TextComponentString(notice),
						detailX + detailWidth - font.getStringWidth(notice) - 3, y + 22, 0xFFFF55);
			}
			drawString(font, new TextComponentTranslation("label.orespawn.biome.placement_rules"),
					detailX + 3, detailRuleTop - 10, 0xCCCCCC);
		}
		if (message != null) drawCenteredString(font, new TextComponentString(message), width / 2, height - 39, 0xFF5555);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}

	static int contentWidth(int width) { return Math.min(520, Math.max(300, width - 20)); }
	static int leftPaneWidth(int width) { return Math.max(132, (contentWidth(width) * 43) / 100); }
	static int listHeight(int height) { return Math.max(32, height - CONTENT_TOP - 36); }
}
