package team.thegoldenhoe.cameraobscura.common;

import com.mia.craftstudio.minecraft.CraftStudioModelWrapper;
import com.mia.craftstudio.minecraft.ModelMetadata;
import net.minecraft.item.Item;
import net.minecraft.world.World;
import team.thegoldenhoe.cameraobscura.common.craftstudio.TilePictureFrame;
import team.thegoldenhoe.cameraobscura.utils.ModelHandler;
import team.thegoldenhoe.cameraobscura.utils.SoundRegistry;

import java.util.ArrayList;

public class CommonProxy {
    public void preInit() {
    }

    public void init() {
    }

    /**
     * https://mcforge.readthedocs.io/en/latest/models/using/#item-models
     */
    public void setModelResourceLocation(final Item item, final int meta, final String name, final String variant) {
    }


    public void setupModelWrappers() {
        // TODO, this might be better server in CSLib somehow, instead of replicated here and in ClientProxy
        final ArrayList<ModelMetadata> models = new ArrayList<ModelMetadata>(ModelHandler.getAllModelMetadata());

        for (final ModelMetadata modelData : models) {
            new CraftStudioModelWrapper(modelData);
        }

        SoundRegistry.registerAllSounds(ModelHandler.getAllModelMetadata());
    }

    public int uploadPictureToGPU(final int oldID, final String pictureLocation, TilePictureFrame.Status status, final float aspectRatio) {
        return 0;
    }

    public World getClientWorld() {
        return null;
    }
}
