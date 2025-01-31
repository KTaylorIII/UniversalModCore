package cam72cam.mod.render;

import cam72cam.mod.item.ItemStack;
import cam72cam.mod.render.opengl.RenderContext;
import cam72cam.mod.render.opengl.RenderState;
import cam72cam.mod.render.opengl.Texture;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.util.With;
import net.minecraft.block.Block;
import net.minecraft.block.BlockColored;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.lwjgl.opengl.GL11;
import util.Matrix4;

import java.util.ArrayList;
import java.util.List;

/** A model that can render both standard MC constructs and custom OpenGL */
public class StandardModel {
    private final List<Pair<IBlockState, IBakedModel>> models = new ArrayList<Pair<IBlockState, IBakedModel>>() {
        @Override
        public boolean add(Pair<IBlockState, IBakedModel> o) {
            worldRenderer = null;
            return super.add(o);
        }
    };
    private final List<RenderFunction> custom = new ArrayList<>();

    /** Hacky way to turn an item into a blockstate, probably has some weird edge cases */
    private static IBlockState itemToBlockState(cam72cam.mod.item.ItemStack stack) {
        Block block = Block.getBlockFromItem(stack.internal.getItem());
        @SuppressWarnings("deprecation")
        IBlockState gravelState = block.getStateFromMeta(stack.internal.getMetadata());
        if (block instanceof BlockLog) {
            gravelState = gravelState.withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.Z);
        }
        return gravelState;
    }

    /** Add a block with a solid color */
    public StandardModel addColorBlock(Color color, Matrix4 transform) {
        IBlockState state = Blocks.CONCRETE.getDefaultState();
        state = state.withProperty(BlockColored.COLOR, color.internal);
        IBakedModel model = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
        models.add(Pair.of(state, new BakedScaledModel(model, transform)));
        return this;
    }

    /** Add snow layers */
    public StandardModel addSnow(int layers, Matrix4 transform) {
        layers = Math.max(1, Math.min(8, layers));
        IBlockState state = Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, layers);
        IBakedModel model = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
        models.add(Pair.of(state, new BakedScaledModel(model, transform)));
        return this;
    }

    /** Add item as a block (best effort) */
    public StandardModel addItemBlock(ItemStack bed, Matrix4 transform) {
        IBlockState state = itemToBlockState(bed);
        IBakedModel model = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes().getModelForState(state);
        models.add(Pair.of(state, new BakedScaledModel(model, transform)));
        return this;
    }

    /** Add item (think dropped item) */
    public StandardModel addItem(ItemStack stack, Matrix4 transform) {
        custom.add((matrix, pt) -> {
            // Hack GLStateManager...
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

            matrix.model_view().multiply(transform);
            try (With ctx = RenderContext.apply(matrix)) {
                Minecraft.getMinecraft().getRenderItem().renderItem(stack.internal, ItemCameraTransforms.TransformType.NONE);
            }
        });
        return this;
    }

    /** Do whatever you want here! */
    public StandardModel addCustom(RenderFunction fn) {
        this.custom.add(fn);
        return this;
    }

    /** Get the quads for the MC standard rendering */
    List<BakedQuad> getQuads(EnumFacing side, long rand) {
        List<BakedQuad> quads = new ArrayList<>();
        for (Pair<IBlockState, IBakedModel> model : models) {
            quads.addAll(model.getValue().getQuads(model.getKey(), side, rand));
        }

        return quads;
    }

    /** Render this entire model
     * @param state*/
    public void render(RenderState state) {
        render(0, state);
    }

    /** Render this entire model (partial tick aware) */
    public void render(float partialTicks, RenderState state) {
        renderCustom(state, partialTicks);
        renderQuads(state);
    }

    private BufferBuilder worldRenderer = null;
    /** Render only the MC quads in this model
     * @param state*/
    public void renderQuads(RenderState state) {
        if (worldRenderer == null) {
            List<BakedQuad> quads = new ArrayList<>();
            for (Pair<IBlockState, IBakedModel> model : models) {
                quads.addAll(model.getRight().getQuads(null, null, 0));
                for (EnumFacing facing : EnumFacing.values()) {
                    quads.addAll(model.getRight().getQuads(null, facing, 0));
                }

            }
            if (quads.isEmpty()) {
                return;
            }

            Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

            worldRenderer = new BufferBuilder(2048) {
                @Override
                public void reset() {
                    //super.reset();
                }
            };
            worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            quads.forEach(quad -> LightUtil.renderQuadColor(worldRenderer, quad, -1));
            worldRenderer.finishDrawing();
        }
        try (With ctx = RenderContext.apply(state.clone().texture(Texture.wrap(new Identifier(TextureMap.LOCATION_BLOCKS_TEXTURE))))) {
            new WorldVertexBufferUploader().draw(worldRenderer);
        }
    }

    /** Render the OpenGL parts directly
     * @param state*/
    public void renderCustom(RenderState state) {
        renderCustom(state, 0);
    }

    /** Render the OpenGL parts directly (partial tick aware) */
    public void renderCustom(RenderState state, float partialTicks) {
        custom.forEach(cons -> cons.render(state.clone(), partialTicks));
    }

    /** Is there anything that's not MC standard in this model? */
    public boolean hasCustom() {
        return !custom.isEmpty();
    }
}
