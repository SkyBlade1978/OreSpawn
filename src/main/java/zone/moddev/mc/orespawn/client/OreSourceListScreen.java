package zone.moddev.mc.orespawn.client;

import java.util.List;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceGroup;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;

/** Height-aware directory of material/domain source conflicts. */
final class OreSourceListScreen extends OreSpawnScreen {
	private static final int ROW_HEIGHT = 38;
	private final GuiScreen parent;
	private final GeologyEditorSession session;
	private int page;
	private int pageSize;
	private int contentLeft;
	private int contentWidth;

	OreSourceListScreen(GuiScreen parent, GeologyEditorSession session) {
		super(new TextComponentTranslation("screen.orespawn.ore_sources"));
		this.parent = parent;
		this.session = session;
	}

	@Override
	protected void init() {
		OreSpawnScreenLayout.beginHelp(this);
		List<OreSourceGroup> groups = session.oreSourceGroups();
		contentWidth = Math.min(390, Math.max(240, width - 24));
		contentLeft = (width - contentWidth) / 2;
		int listTop = 34;
		int controlsY = height - 52;
		pageSize = Math.max(1, (controlsY - listTop) / ROW_HEIGHT);
		int pages = pageCount(groups.size(), pageSize);
		page = Math.max(0, Math.min(page, pages - 1));
		int start = page * pageSize;
		for (int index = 0; index < pageSize && start + index < groups.size(); index++) {
			OreSourceGroup group = groups.get(start + index);
			int y = listTop + index * ROW_HEIGHT;
			addButton(new Button(contentLeft, y, contentWidth, 34,
					OreSpawnScreenLayout.fit(font, new TextComponentString(rowLabel(group)), contentWidth - 8),
					button -> minecraft.displayGuiScreen(new OreSourceDetailScreen(this, session, group.key)),
					(button, mouseX, mouseY) -> renderStringTooltip(tooltip(group), mouseX, mouseY)));
		}
		Button previous = addButton(new Button(contentLeft, controlsY, 80, 20,
				new TextComponentTranslation("button.orespawn.previous"),
				button -> { page--; rebuild(); }));
		Button next = addButton(new Button(contentLeft + contentWidth - 80, controlsY, 80, 20,
				new TextComponentTranslation("button.orespawn.next"),
				button -> { page++; rebuild(); }));
		previous.enabled = page > 0;
		next.enabled = page + 1 < pages;
		addButton(new Button(width / 2 - 75, height - 28, 150, 20,
				DialogTexts.GUI_DONE, button -> onClose()));
	}

	private String rowLabel(OreSourceGroup group) {
		return I18n.format("label.orespawn.ore_source.row", display(group.material), display(group.domain),
				group.candidates.size(), I18n.format("status.orespawn.ore_source." + group.status));
	}

	private java.util.List<String> tooltip(OreSourceGroup group) {
		java.util.List<String> result = new java.util.ArrayList<>();
		result.add(I18n.format("label.orespawn.ore_source.material", group.material));
		result.add(I18n.format("label.orespawn.ore_source.domain", group.domain));
		result.add(I18n.format("label.orespawn.ore_source.channels", group.channels().size()));
		result.add(I18n.format("tooltip.orespawn.ore_source.open"));
		return result;
	}

	private static String display(String id) {
		int split = id.indexOf(':');
		String value = split < 0 ? id : id.substring(split + 1);
		return value.replace('_', ' ');
	}

	private void rebuild() {
		buttons.clear(); children.clear(); init();
	}

	static int pageCount(int entries, int pageSize) {
		return Math.max(1, (Math.max(0, entries) + Math.max(1, pageSize) - 1) / Math.max(1, pageSize));
	}

	@Override
	public void onClose() { minecraft.displayGuiScreen(parent); }

	@Override
	public void render(int mouseX, int mouseY, float partialTick) {
		renderBackground();
		drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
		List<OreSourceGroup> groups = session.oreSourceGroups();
		if (groups.isEmpty()) drawCenteredString(font,
				new TextComponentTranslation("label.orespawn.ore_source.none"), width / 2, height / 2, 0xA0A0A0);
		drawCenteredString(font, new TextComponentString((page + 1) + " / " + pageCount(groups.size(), pageSize)),
				width / 2, height - 46, 0xA0A0A0);
		super.render(mouseX, mouseY, partialTick);
		OreSpawnScreenLayout.renderExplanations(this, mouseX, mouseY);
	}
}
