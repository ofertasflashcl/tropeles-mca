package net.mca.network;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.c2s.*;
import net.mca.network.s2c.*;

public interface MessagesMCA {
    static void bootstrap() {
        NetworkHandler.registerMessage(InteractionVillagerMessage.class, InteractionVillagerMessage::decode, true);
        NetworkHandler.registerMessage(BabyNamingVillagerMessage.class, BabyNamingVillagerMessage::decode, true);
        NetworkHandler.registerMessage(GetFamilyRequest.class, GetFamilyRequest::decode, true);
        NetworkHandler.registerMessage(GetFamilyResponse.class, GetFamilyResponse::decode, false);
        NetworkHandler.registerMessage(GetVillagerResponse.class, GetVillagerResponse::decode, false);
        NetworkHandler.registerMessage(CallToPlayerMessage.class, CallToPlayerMessage::decode, true);
        NetworkHandler.registerMessage(GetVillageRequest.class, GetVillageRequest::decode, true);
        NetworkHandler.registerMessage(GetVillageResponse.class, GetVillageResponse::decode, false);
        NetworkHandler.registerMessage(GetVillageFailedResponse.class, GetVillageFailedResponse::decode, false);
        NetworkHandler.registerMessage(OpenGuiRequest.class, OpenGuiRequest::decode, false);
        NetworkHandler.registerMessage(ReportBuildingMessage.class, ReportBuildingMessage::decode, true);
        NetworkHandler.registerMessage(SaveVillageMessage.class, SaveVillageMessage::decode, true);
        NetworkHandler.registerMessage(GetFamilyTreeRequest.class, GetFamilyTreeRequest::decode, true);
        NetworkHandler.registerMessage(GetFamilyTreeResponse.class, GetFamilyTreeResponse::decode, false);
        NetworkHandler.registerMessage(GetInteractDataRequest.class, GetInteractDataRequest::decode, true);
        NetworkHandler.registerMessage(GetInteractDataResponse.class, GetInteractDataResponse::decode, false);
        NetworkHandler.registerMessage(InteractionDialogueMessage.class, InteractionDialogueMessage::decode, true);
        NetworkHandler.registerMessage(InteractionDialogueResponse.class, InteractionDialogueResponse::decode, false);
        NetworkHandler.registerMessage(InteractionDialogueInitMessage.class, InteractionDialogueInitMessage::decode, true);
        NetworkHandler.registerMessage(GetVillagerRequest.class, GetVillagerRequest::decode, true);
        NetworkHandler.registerMessage(VillagerEditorSyncRequest.class, VillagerEditorSyncRequest::decode, true);
        NetworkHandler.registerMessage(AnalysisResults.class, AnalysisResults::decode, false);
        NetworkHandler.registerMessage(InteractionCloseRequest.class, InteractionCloseRequest::decode, true);
        NetworkHandler.registerMessage(ShowToastRequest.class, ShowToastRequest::decode, false);
        NetworkHandler.registerMessage(BabyNameRequest.class, BabyNameRequest::decode, true);
        NetworkHandler.registerMessage(BabyNameResponse.class, BabyNameResponse::decode, false);
        NetworkHandler.registerMessage(VillagerNameRequest.class, VillagerNameRequest::decode, true);
        NetworkHandler.registerMessage(VillagerNameResponse.class, VillagerNameResponse::decode, false);
        NetworkHandler.registerMessage(RenameVillageMessage.class, RenameVillageMessage::decode, true);
        NetworkHandler.registerMessage(FamilyTreeUUIDLookup.class, FamilyTreeUUIDLookup::decode, true);
        NetworkHandler.registerMessage(FamilyTreeUUIDResponse.class, FamilyTreeUUIDResponse::decode, false);
        NetworkHandler.registerMessage(DestinyMessage.class, DestinyMessage::decode, true);
        NetworkHandler.registerMessage(PlayerDataMessage.class, PlayerDataMessage::decode, false);
        NetworkHandler.registerMessage(PlayerDataRequest.class, PlayerDataRequest::decode, true);
        NetworkHandler.registerMessage(SkinListRequest.class, SkinListRequest::decode, true);
        NetworkHandler.registerMessage(SkinListResponse.class, SkinListResponse::decode, false);
        NetworkHandler.registerMessage(OpenDestinyGuiRequest.class, OpenDestinyGuiRequest::decode, false);
        NetworkHandler.registerMessage(DamageItemMessage.class, DamageItemMessage::decode, true);
        NetworkHandler.registerMessage(InteractionDialogueQuestionResponse.class, InteractionDialogueQuestionResponse::decode, false);
        NetworkHandler.registerMessage(ConfigRequest.class, ConfigRequest::decode, true);
        NetworkHandler.registerMessage(ConfigResponse.class, ConfigResponse::decode, false);
        NetworkHandler.registerMessage(VillagerMessage.class, VillagerMessage::decode, false);
        NetworkHandler.registerMessage(AddCustomClothingMessage.class, AddCustomClothingMessage::decode, true);
        NetworkHandler.registerMessage(RemoveCustomClothingMessage.class, RemoveCustomClothingMessage::decode, true);
        NetworkHandler.registerMessage(CustomSkinsChangedMessage.class, CustomSkinsChangedMessage::decode, false);
        NetworkHandler.registerMessage(SetTargetMessage.class, SetTargetMessage::decode, true);
        NetworkHandler.registerMessage(CivilRegistryPageRequest.class, CivilRegistryPageRequest::decode, true);
        NetworkHandler.registerMessage(CivilRegistryResponse.class, CivilRegistryResponse::decode, false);
        NetworkHandler.registerMessage(net.mca.network.s2c.FantasmaMessage.class, net.mca.network.s2c.FantasmaMessage::decode, false);
    }
}
