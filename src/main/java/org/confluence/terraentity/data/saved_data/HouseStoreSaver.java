package org.confluence.terraentity.data.saved_data;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.confluence.terraentity.entity.npc.house.HouseManager;

// 暂时还只存主世界，后面要存储维度信息
public class HouseStoreSaver extends SavedData {

    public static final String NAME = "house_storage";

    public static HouseStoreSaver create() {
        return new HouseStoreSaver();
    }


    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        JsonElement jsonElement = HouseManager.CODEC.encodeStart(JsonOps.INSTANCE, HouseManager.getInstance()).result().get();
        tag.putString(NAME, jsonElement.toString());
        return tag;
    }

    public HouseStoreSaver load(CompoundTag nbt, HolderLookup.Provider registries) {
        HouseStoreSaver data = create();
        HouseManager.getInstance().load(HouseManager.CODEC.decode(JsonOps.INSTANCE, GsonHelper.parse(nbt.getString(NAME))).result().get().getFirst());
        return data;
    }


    public static HouseStoreSaver decode(CompoundTag tag, HolderLookup.Provider registries){
        HouseStoreSaver modLevelSaveData = HouseStoreSaver.create();
        modLevelSaveData.load(tag,registries);
        return modLevelSaveData;
    }


    public static HouseStoreSaver get(MinecraftServer server) {
        ServerLevel world = server.overworld();
        DimensionDataStorage dataStorage = world.getDataStorage();

        HouseStoreSaver t = dataStorage.computeIfAbsent(new Factory<>(HouseStoreSaver::create, HouseStoreSaver::decode), HouseStoreSaver.NAME);
        t.setDirty();
        return t;
    }
}
