package zone.moddev.mc.orespawn.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;

/** Small target-native icon button used by the OreSpawn Mods directory. */
final class CogButton extends Button {
	private static final ResourceLocation COG =
			new ResourceLocation("orespawn", "textures/gui/cog.png");

	CogButton(int x, int y, IPressable onPress, Tooltip tooltip) {
		super(x, y, 22, 20, "", onPress, tooltip);
	}

	@Override
	public void drawButton(Minecraft minecraft, int mouseX, int mouseY) {
		super.drawButton(minecraft, mouseX, mouseY);
		if (!visible) return;
		minecraft.getTextureManager().bindTexture(COG);
		if (enabled) GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
		else GlStateManager.color(0.45F, 0.45F, 0.45F, 0.8F);
		drawModalRectWithCustomSizedTexture(xPosition + 3, yPosition + 2,
				0.0F, 0.0F, 16, 16, 16.0F, 16.0F);
		GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
	}
}
