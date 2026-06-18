package com.afriland.approval.service;

import com.afriland.approval.model.AppSetting;
import com.afriland.approval.repository.AppSettingRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Stores the admin-selected card design (global, persisted in DB) and the two
 * template definitions reproduced from the Angular app (DigitalCardApp_afb-master):
 *  - classic -> assets/carte-digitale-bg.png
 *  - modern  -> assets/background_new.jpg
 * Each template is a background image + the inner padding used to lay the text over it.
 */
@Service
public class SettingsService {

    /** A card template: background image + content padding (against the 600x340 card box). */
    public record CardTemplate(String id, String label, String background,
                               int padTop, int padRight, int padBottom, int padLeft,
                               String backgroundSize) {}

    public static final List<CardTemplate> TEMPLATES = List.of(
            new CardTemplate("classic", "Classique", "/img/carte-digitale-bg.png", 113, 32, 20, 100, "cover"),
            new CardTemplate("modern",  "Moderne",   "/img/background_new.jpg",     90, 24, 45, 65, "100% 100%")
    );

    private static final String KEY = "card.design";
    public static final String DEFAULT_DESIGN = "classic";

    private final AppSettingRepository repo;
    public SettingsService(AppSettingRepository repo) { this.repo = repo; }

    public List<CardTemplate> templates() { return TEMPLATES; }

    public CardTemplate byId(String id) {
        return TEMPLATES.stream().filter(t -> t.id().equals(id)).findFirst().orElse(TEMPLATES.get(0));
    }

    public String getActiveDesign() {
        return repo.findBySettingKey(KEY)
                .map(AppSetting::getSettingValue)
                .filter(v -> TEMPLATES.stream().anyMatch(t -> t.id().equals(v)))
                .orElse(DEFAULT_DESIGN);
    }

    public CardTemplate activeTemplate() { return byId(getActiveDesign()); }

    public void setActiveDesign(String id) {
        String d = TEMPLATES.stream().anyMatch(t -> t.id().equals(id)) ? id : DEFAULT_DESIGN;
        AppSetting s = repo.findBySettingKey(KEY).orElseGet(() -> {
            AppSetting n = new AppSetting();
            n.setSettingKey(KEY);
            return n;
        });
        s.setSettingValue(d);
        repo.save(s);
    }
}
