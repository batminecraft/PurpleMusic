package fr.batmultifonction.purplemusic.audio;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.VolumeCategory;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent;

import javax.annotation.Nullable;

public class VoicePlugin implements VoicechatPlugin {

    public static final String CATEGORY_ID = "purple_music";

    public static VoicechatApi voicechatApi;
    @Nullable public static VoicechatServerApi voicechatServerApi;
    @Nullable public static VolumeCategory category;

    @Override
    public String getPluginId() {
        return "purplemusic";
    }

    @Override
    public void initialize(VoicechatApi api) {
        voicechatApi = api;
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(VoicechatServerStartedEvent.class, this::onServerStarted);
    }

    private void onServerStarted(VoicechatServerStartedEvent event) {
        voicechatServerApi = event.getVoicechat();
        category = voicechatServerApi.volumeCategoryBuilder()
                .setId(CATEGORY_ID)
                .setName("PurpleMusic")
                .setDescription("Volume of music played by PurpleMusic")
                .build();
        voicechatServerApi.registerVolumeCategory(category);
    }
}
