package com.thatguycy.worlddynamicsengine.manager.text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;

/** Java implementation of TynanArtSimulator's weighted rule and template engine. */
final class TynanTextComposer {
    private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\[([A-Za-z0-9_]+)]");
    private static final String[] STYLE_LABELS_ZH = {
            "象征某个概念", "概念启发 + 艺术风格", "充满某艺术风格气息", "中央与背景双风格对比",
            "风格气息 + 构图", "整体构图方法", "融合双艺术风格", "表现两个概念",
            "两个概念反差", "构图与风格冲突", "主色调双色", "形状构图"
    };
    private static final String[] STYLE_LABELS_EN = {
            "Symbolizes a concept", "Concept + art style", "Strong art-style aura",
            "Dual style contrast", "Style + composition", "Overall composition",
            "Blended dual styles", "Expresses two concepts", "Concept contrast",
            "Composition vs style", "Dual color palette", "Shape composition"
    };
    private static final List<TynanTextCompositionManager.ConceptTone> CONCEPT_TONES = List.of(
            new TynanTextCompositionManager.ConceptTone("auto", "随机情绪", "Random tone"),
            new TynanTextCompositionManager.ConceptTone("positive", "正向 / 温暖", "Positive"),
            new TynanTextCompositionManager.ConceptTone("badass", "硬核 / 霸气", "Badass"),
            new TynanTextCompositionManager.ConceptTone("angsty", "阴郁 / angst", "Angsty"));

    private final Map<String, Object> data;
    private final RimWorldCalendar calendar;

    TynanTextComposer(ConfigurationSection section) {
        data = toStringMap(section);
        calendar = new RimWorldCalendar(map(data.get("calendar")));
    }

    List<TynanGeneratedText> generate(TynanCompositionRequest request) {
        Map<String, Object> tale = findById(list(data.get("tales")), request.taleId());
        Map<String, Object> carrier = findById(list(data.get("carriers")), request.carrierId());
        if (tale == null) {
            throw new IllegalArgumentException("Unknown Tynan tale: " + request.taleId());
        }
        if (carrier == null || !bool(carrier.get("available"), true)) {
            throw new IllegalArgumentException("Unknown or unavailable Tynan carrier: " + request.carrierId());
        }

        List<TynanGeneratedText> results = new ArrayList<>();
        for (int index = 0; index < request.count(); index++) {
            long seed = request.seed().isBlank()
                    ? System.currentTimeMillis() + index * 9973L
                    : hashSeed(request.seed() + "#" + index);
            results.add(generateOne(tale, carrier, request, seed));
        }
        return List.copyOf(results);
    }

    TynanGeneratedText generateEventQuest(String eventId, String language,
                                          Map<String, String> slots, long seed) {
        String lang = normalizeLanguage(language);
        Map<String, Object> quest = map(map(data.get("eventQuests")).get(eventId));
        if (quest.isEmpty()) {
            throw new IllegalArgumentException("Unknown Tynan event quest: " + eventId);
        }

        Map<String, String> context = new LinkedHashMap<>();
        if (slots != null) {
            slots.forEach((key, value) -> {
                if (key != null && value != null) {
                    context.put(key, value);
                }
            });
        }
        context.putIfAbsent("DATE", calendar.date(lang, seed));
        Random random = new Random(seed);
        List<String> paragraphs = new ArrayList<>();
        for (String section : List.of("opening", "problem", "proposal", "risk", "terms")) {
            List<WeightedText> choices = new ArrayList<>();
            for (Map<String, Object> choice : maps(quest.get(section))) {
                String text = localized(choice, lang,
                        string(choice.get("en")).isBlank()
                                ? string(choice.get("zh")) : string(choice.get("en")));
                if (!text.isBlank()) {
                    choices.add(new WeightedText(text, decimal(choice.get("weight"), 1.0D)));
                }
            }
            String template = pick(choices, random);
            if (template.isBlank()) {
                continue;
            }
            String paragraph = resolveTemplate(template, lang, context, Map.of(),
                    random, 0, new HashMap<>(), "");
            paragraph = finishSentence(normalizeSpaces(paragraph, lang), lang);
            if (!paragraph.isBlank()) {
                paragraphs.add(paragraph);
            }
        }
        if (paragraphs.size() != 5) {
            throw new IllegalStateException(
                    "Tynan event quest must define all five narrative sections: " + eventId);
        }
        return new TynanGeneratedText(
                String.join("\n\n", paragraphs), seed, lang, eventId, "event_quest", "quest", false);
    }

    List<TynanTextCompositionManager.TaleOption> tales(String language, String carrierId) {
        String lang = normalizeLanguage(language);
        List<TynanTextCompositionManager.TaleOption> result = new ArrayList<>();
        for (Map<String, Object> tale : maps(data.get("tales"))) {
            List<String> carrierIds = strings(tale.get("carrierIds"));
            if (carrierId != null && !carrierId.isBlank()
                    && !carrierIds.isEmpty() && !carrierIds.contains(carrierId)) {
                continue;
            }
            Map<String, Object> label = map(tale.get("label"));
            String displayName = localized(label, lang, string(tale.get("id")));
            result.add(new TynanTextCompositionManager.TaleOption(
                    string(tale.get("id")), displayName, string(tale.get("package")),
                    carrierIds, integer(tale.get("ruleCount"), 0), map(tale.get("slots"))));
        }
        return List.copyOf(result);
    }

    List<TynanTextCompositionManager.CarrierOption> carriers(String language) {
        String lang = normalizeLanguage(language);
        List<TynanTextCompositionManager.CarrierOption> result = new ArrayList<>();
        for (Map<String, Object> carrier : maps(data.get("carriers"))) {
            Map<String, Object> label = map(carrier.get("label"));
            result.add(new TynanTextCompositionManager.CarrierOption(
                    string(carrier.get("id")), localized(label, lang, string(carrier.get("id"))),
                    bool(carrier.get("available"), true)));
        }
        return List.copyOf(result);
    }

    List<TynanTextCompositionManager.StyleClauseOption> styleClauses(String language) {
        String lang = normalizeLanguage(language);
        List<WeightedText> rules = ruleLists(
                dataRulePack("ArtDescriptionUtility_Global"), "style_clause", lang);
        List<TynanTextCompositionManager.StyleClauseOption> result = new ArrayList<>();
        for (int index = 0; index < rules.size(); index++) {
            WeightedText rule = rules.get(index);
            String id = "sc" + index;
            result.add(new TynanTextCompositionManager.StyleClauseOption(
                    id, styleLabel(index, lang), rule.text(), rule.weight(),
                    rule.text().contains("[artstyle_adj]"), rule.text().contains("[ConceptAny]"),
                    rule.text().contains("[composition]"), rule.text().contains("[Color]")));
        }
        return List.copyOf(result);
    }

    List<TynanTextCompositionManager.ConceptTone> conceptTones() {
        return CONCEPT_TONES;
    }

    List<TynanTextCompositionManager.StyleOption> artStyles(String language) {
        String lang = normalizeLanguage(language);
        List<WeightedText> localizedRules = ruleLists(
                dataRulePack("ArtDescriptionUtility_Global"), "artstyle_adj", lang);
        List<Map<String, Object>> rawRules =
                rawRules("ArtDescriptionUtility_Global", "artstyle_adj");
        Set<String> seen = new HashSet<>();
        List<TynanTextCompositionManager.StyleOption> result = new ArrayList<>();
        for (int index = 0; index < localizedRules.size(); index++) {
            String localized = localizedRules.get(index).text().trim();
            String id = index < rawRules.size() ? string(rawRules.get(index).get("id")).trim() : "";
            if (id.isBlank()) {
                id = localized;
            }
            if (id.isBlank() || !seen.add(id)) {
                continue;
            }
            String zh = "zh".equals(lang) ? localized : "";
            String en = "en".equals(lang) ? localized : "";
            result.add(new TynanTextCompositionManager.StyleOption(id, zh, en));
        }
        return List.copyOf(result);
    }

    Map<String, Object> pronoun(String pronounId) {
        for (Map<String, Object> value : maps(data.get("pronouns"))) {
            if (string(value.get("id")).equalsIgnoreCase(pronounId)) {
                return value;
            }
        }
        return null;
    }

    private TynanGeneratedText generateOne(Map<String, Object> tale, Map<String, Object> carrier,
                                           TynanCompositionRequest request, long seed) {
        String lang = request.language();
        Random random = new Random(seed);
        boolean hasTaleRules = hasTaleRules(tale);
        boolean useTale = switch (request.branch()) {
            case "tale" -> hasTaleRules;
            case "taleless" -> false;
            default -> hasTaleRules && random.nextDouble() > 0.2D;
        };

        Map<String, List<BiText>> ruleIndex = buildRuleIndex(
                tale, useTale, string(carrier.get("rulePackId")));
        Map<String, String> context = hydrateContext(request.slots(), tale, lang, seed);
        Map<String, Object> artStyle = findArtStyle(request.artStyle(), lang);
        if (artStyle != null) {
            context.put("artstyle_adj", localized(
                    artStyle, lang, string(artStyle.get("id"))));
        }
        String conceptPool = switch (request.conceptTone()) {
            case "positive" -> "ConceptPositive";
            case "badass" -> "ConceptBadass";
            case "angsty" -> "ConceptAngsty";
            default -> "";
        };

        String mode = request.mode();
        String text = "";
        if ("strict".equals(mode)) {
            List<WeightedText> roots = getRules(ruleIndex, "r_art_description", lang);
            if (roots.isEmpty()) {
                mode = "light";
            } else {
                List<WeightedText> filtered = roots.stream()
                        .filter(rule -> useTale
                                ? rule.text().contains("context_sentence")
                                    || rule.text().contains("physical_description_root")
                                : !rule.text().contains("context_sentence"))
                        .toList();
                text = resolveTemplate(pick(filtered.isEmpty() ? roots : filtered, random),
                        lang, context, ruleIndex, random, 0, new HashMap<>(), conceptPool);
            }
        }
        if ("light".equals(mode)) {
            text = generateLight(tale, request, useTale, lang, context, ruleIndex,
                    random, conceptPool, artStyle != null);
        }
        text = finishSentence(polishText(normalizeSpaces(text, lang), lang), lang);
        return new TynanGeneratedText(
                text, seed, lang, string(tale.get("id")), string(carrier.get("id")), mode, useTale);
    }

    private String generateLight(Map<String, Object> tale, TynanCompositionRequest request,
                                 boolean useTale, String lang, Map<String, String> context,
                                 Map<String, List<BiText>> ruleIndex, Random random,
                                 String conceptPool, boolean hasArtStyle) {
        String physical = pick(getRules(ruleIndex, "physical_description_root", lang), random);
        if (physical.isBlank()) {
            physical = "zh".equals(lang)
                    ? "这件作品描绘了[image]。[desc_sentence_group]"
                    : "This work depicts [image]. [desc_sentence_group]";
        }
        Map<String, List<Map<String, Object>>> taleRules = mapOfLists(
                map(tale.get("rulesByKeyword")));
        String image = localOrGlobal("image", taleRules, useTale, lang, ruleIndex, random);
        String circumstance = localOrGlobal(
                "circumstance_phrase", taleRules, useTale, lang, ruleIndex, random);
        String description = localOrGlobal(
                "desc_sentence", taleRules, useTale, lang, ruleIndex, random);
        String taleNoun = localOrGlobal("tale_noun", taleRules, useTale, lang, ruleIndex, random);

        List<TynanTextCompositionManager.StyleClauseOption> allClauses = styleClauses(lang);
        List<TynanTextCompositionManager.StyleClauseOption> selectedClauses =
                filterStyleClauses(allClauses, request.styleClauseIds());
        boolean mustStyle = hasArtStyle || !request.styleClauseIds().isEmpty();
        String style = "";
        if (request.styleLevel() > 0.0D || mustStyle) {
            List<WeightedText> pool = selectedClauses.stream()
                    .map(option -> new WeightedText(option.template(), option.weight()))
                    .filter(option -> !option.text().isBlank())
                    .toList();
            if (hasArtStyle) {
                List<WeightedText> withStyle = pool.stream()
                        .filter(option -> option.text().contains("[artstyle_adj]"))
                        .toList();
                if (!withStyle.isEmpty()) {
                    pool = withStyle;
                }
            }
            if (!mustStyle && request.styleLevel() < 1.5D && random.nextDouble() < 0.35D) {
                style = "";
            } else if (!pool.isEmpty()) {
                style = pick(pool, random);
            } else if (hasArtStyle) {
                style = "zh".equals(lang)
                        ? "作品充满了浓烈的[artstyle_adj]气息"
                        : "the work is executed in a [artstyle_adj] style";
            }
        }

        image = resolveTemplate(image, lang, context, ruleIndex, random, 0, new HashMap<>(), conceptPool)
                .trim().replaceAll("[。.!?]+$", "");
        circumstance = finishSentence(resolveTemplate(
                circumstance, lang, context, ruleIndex, random, 0, new HashMap<>(), conceptPool), lang);
        description = finishSentence(resolveTemplate(
                description, lang, context, ruleIndex, random, 0, new HashMap<>(), conceptPool), lang);
        style = finishSentence(resolveTemplate(
                style, lang, context, ruleIndex, random, 0, new HashMap<>(), conceptPool), lang);
        taleNoun = resolveTemplate(taleNoun, lang, context, ruleIndex, random, 0,
                new HashMap<>(), conceptPool).trim().replaceAll("[。.!?]+$", "");
        taleNoun = repairTaleNoun(taleNoun, context, lang);

        Map<String, String> localContext = new HashMap<>(context);
        localContext.put("image", image);
        localContext.put("circumstance_phrase", circumstance);
        localContext.put("circumstance_group", circumstance);
        localContext.put("desc_sentence", description);
        localContext.put("style_group", style);
        localContext.put("desc_sentence_group", joinSentences(List.of(description, style), lang));
        localContext.put("tale_noun", taleNoun);

        Map<String, String> contextContext = new HashMap<>(localContext);
        if ("zh".equals(lang)) {
            String[] depictions = {"作品", "画面", "图像"};
            contextContext.put("depiction", depictions[random.nextInt(depictions.length)]);
        } else {
            String[] depictions = {"work", "image", "piece"};
            contextContext.put("depiction", depictions[random.nextInt(depictions.length)]);
        }
        String result = finishSentence(resolveTemplate(
                physical, lang, localContext, ruleIndex, random, 0, new HashMap<>(), conceptPool), lang);
        if (useTale && request.includeContext()) {
            String contextTemplate = "";
            if ("zh".equals(lang)) {
                contextTemplate = pick(List.of(
                        new WeightedText("作品中所引述的故事发生在[DATE]，讲述了[tale_noun]。", 1),
                        new WeightedText("这件作品描述了发生在[DATE]的[tale_noun]。", 1),
                        new WeightedText("作品记述了发生在[DATE]的关于[tale_noun]的往事。", 1)), random);
            } else {
                contextTemplate = pick(getRules(ruleIndex, "context_sentence", lang), random);
            }
            if (!contextTemplate.isBlank()) {
                String extra = resolveTemplate(contextTemplate, lang, contextContext,
                        ruleIndex, random, 0, new HashMap<>(), conceptPool);
                result = joinSentences(List.of(result, extra), lang);
            }
        }
        return result;
    }

    private Map<String, List<BiText>> buildRuleIndex(
            Map<String, Object> tale, boolean useTale, String carrierRulePackId) {
        Map<String, List<BiText>> index = new LinkedHashMap<>();
        Map<String, Object> packIds = map(data.get("systemRulePackIds"));
        Map<String, Object> packs = map(data.get("rulepacks"));
        addRulePack(index, packs.get(string(packIds.get("globalUtility"))));
        addRulePack(index, packs.get(string(packIds.get("style"))));
        addRulePack(index, packs.get(string(packIds.get("talelessImages"))));
        addRulePack(index, packs.get(string(packIds.get("hasTaleRoot"))));
        addRulePack(index, packs.get(string(packIds.get("talelessRoot"))));
        addRulePack(index, packs.get(carrierRulePackId));
        if (useTale) {
            addRuleSet(index, map(tale.get("rulesByKeyword")));
        }
        return index;
    }

    private void addRulePack(Map<String, List<BiText>> index, Object value) {
        addRuleSet(index, map(map(value).get("rulesByKeyword")));
    }

    private void addRuleSet(Map<String, List<BiText>> index, Map<String, Object> rules) {
        for (Map.Entry<String, Object> entry : rules.entrySet()) {
            List<BiText> bucket = index.computeIfAbsent(entry.getKey(), ignored -> new ArrayList<>());
            for (Map<String, Object> rule : maps(entry.getValue())) {
                String en = string(rule.get("en"));
                String zh = string(rule.get("zh"));
                double weight = decimal(rule.get("weight"), 1.0D);
                bucket.add(new BiText(en, zh, weight));
            }
        }
    }

    private List<WeightedText> getRules(
            Map<String, List<BiText>> index, String keyword, String language) {
        List<BiText> rules = index.getOrDefault(keyword, List.of());
        if (rules.isEmpty()) {
            return List.of();
        }
        return rules.stream()
                .map(rule -> new WeightedText("en".equals(language)
                        ? fallback(rule.en(), rule.zh())
                        : fallback(rule.zh(), rule.en()), rule.weight()))
                .filter(rule -> !rule.text().isBlank())
                .toList();
    }

    private String localOrGlobal(String keyword, Map<String, List<Map<String, Object>>> taleRules,
                                 boolean useTale, String language,
                                 Map<String, List<BiText>> ruleIndex, Random random) {
        List<Map<String, Object>> localRules = taleRules.getOrDefault(keyword, List.of());
        if (useTale && !localRules.isEmpty()) {
            List<WeightedText> localized = new ArrayList<>();
            for (Map<String, Object> rule : localRules) {
                String en = string(rule.get("en"));
                String zh = string(rule.get("zh"));
                localized.add(new WeightedText(
                        "en".equals(language) ? fallback(en, zh) : fallback(zh, en),
                        decimal(rule.get("weight"), 1.0D)));
            }
            return pick(localized, random);
        }
        return pick(getRules(ruleIndex, keyword, language), random);
    }

    private String resolveTemplate(String template, String language, Map<String, String> context,
                                   Map<String, List<BiText>> ruleIndex, Random random,
                                   int depth, Map<String, Integer> used, String conceptPool) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        if (depth > 12) {
            return template;
        }
        Matcher matcher = TEMPLATE_TOKEN.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = context.getOrDefault(key, "");
            if (replacement.isBlank() && key.contains("_")) {
                String[] pieces = key.split("_", 2);
                replacement = lookupPersonField(context, pieces[0], pieces[1]);
            }
            if (replacement.isBlank()) {
                List<WeightedText> rules = getRules(ruleIndex, key, language);
                if (!rules.isEmpty()) {
                    String marker = language + ":" + key;
                    int count = used.getOrDefault(marker, 0);
                    if (count <= 4) {
                        used.put(marker, count + 1);
                        replacement = resolveTemplate(
                                pick(rules, random), language, context, ruleIndex, random,
                                depth + 1, used, conceptPool);
                    }
                } else {
                    replacement = wordPick(key, language, random, conceptPool);
                    if (!replacement.isBlank()) {
                        replacement = resolveTemplate(
                                replacement, language, context, ruleIndex, random,
                                depth + 1, used, conceptPool);
                    }
                }
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private String wordPick(String keyword, String language, Random random, String conceptPool) {
        Map<String, Object> aliases = map(data.get("utilityAliases"));
        List<String> alternatives = strings(aliases.get(keyword));
        String resolvedKeyword = !conceptPool.isBlank() && "ConceptAny".equals(keyword)
                ? conceptPool
                : alternatives.isEmpty()
                    ? keyword : alternatives.get(random.nextInt(alternatives.size()));
        Map<String, Object> pool = map(map(data.get("wordPools")).get(resolvedKeyword));
        List<String> words = strings(pool.get(language));
        if (words.isEmpty()) {
            words = strings(pool.get("zh"));
        }
        if (words.isEmpty()) {
            words = strings(pool.get("en"));
        }
        return words.isEmpty() ? "" : words.get(random.nextInt(words.size()));
    }

    private Map<String, String> hydrateContext(Map<String, String> rawSlots,
                                               Map<String, Object> tale, String language, long seed) {
        Map<String, String> context = new LinkedHashMap<>();
        rawSlots.forEach((key, value) -> {
            if (key != null && value != null && !value.trim().isEmpty()) {
                context.put(key, value.trim());
            }
        });
        context.putIfAbsent("DATE", calendar.date(language, seed));

        Set<String> prefixes = new LinkedHashSet<>();
        Map<String, Object> slots = map(tale.get("slots"));
        for (Map<String, Object> role : maps(slots.get("roles"))) {
            addPrefix(prefixes, string(role.get("prefix")));
        }
        for (Object token : list(slots.get("tokens"))) {
            addPrefixFromToken(prefixes, string(token));
        }
        for (Map<String, Object> slot : maps(slots.get("flat"))) {
            addPrefix(prefixes, string(slot.get("prefix")));
            addPrefixFromToken(prefixes, string(slot.get("token")));
        }
        for (String key : rawSlots.keySet()) {
            addPrefixFromToken(prefixes, key);
        }
        for (String prefix : prefixes) {
            ensurePersonDefaults(context, prefix, language);
        }
        context.putIfAbsent("sculpture", "zh".equals(language) ? "雕塑" : "sculpture");
        return context;
    }

    private void ensurePersonDefaults(Map<String, String> context, String prefix, String language) {
        String fullName = fallback(
                lookupPersonField(context, prefix, "nameFull"),
                lookupPersonField(context, prefix, "nameDef"));
        if (fullName.isBlank()) {
            return;
        }
        List<String> candidates = personPrefixCandidates(prefix);
        String canonical = candidates.stream()
                .filter(value -> value.length() > 1 && value.equals(value.toUpperCase(Locale.ROOT)))
                .findFirst().orElse(candidates.get(0));
        for (String field : List.of("nameFull", "nameDef", "nameIndef",
                "nameShortDef", "nameShortIndef", "label")) {
            context.putIfAbsent(canonical + "_" + field, fullName);
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        context.putIfAbsent(lower + "_nameShortDef", fullName);
        context.putIfAbsent(lower + "_nameDef", fullName);
        context.putIfAbsent(lower + "_nameFull", fullName);
        context.putIfAbsent(canonical + "_pronoun", "zh".equals(language) ? "他" : "he");
        context.putIfAbsent(canonical + "_possessive", "zh".equals(language) ? "他的" : "his");
        context.putIfAbsent(canonical + "_objective", "zh".equals(language) ? "他" : "him");
    }

    private String lookupPersonField(Map<String, String> context, String prefix, String field) {
        List<String> fields = new ArrayList<>();
        fields.add(field);
        if (Set.of("nameShortDef", "nameShortIndef", "nameShort").contains(field)) {
            fields.addAll(List.of("nameDef", "nameFull", "nameIndef"));
        } else if (Set.of("nameDef", "nameIndef").contains(field)) {
            fields.addAll(List.of("nameFull", "nameDef"));
        } else if (Set.of("definite", "indefinite").contains(field)) {
            fields.addAll(List.of("label", "nameFull", "nameDef"));
        }
        for (String candidate : personPrefixCandidates(prefix)) {
            for (String candidateField : fields) {
                String value = context.get(candidate + "_" + candidateField);
                if (value != null && !value.trim().isEmpty()) {
                    return value.trim();
                }
            }
        }
        return "";
    }

    private List<String> personPrefixCandidates(String prefix) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (prefix == null || prefix.isBlank()) {
            return List.of();
        }
        values.add(prefix);
        values.add(prefix.toUpperCase(Locale.ROOT));
        values.add(prefix.toLowerCase(Locale.ROOT));
        values.add(prefix.substring(0, 1).toUpperCase(Locale.ROOT)
                + prefix.substring(1).toLowerCase(Locale.ROOT));
        Map<String, String> aliases = Map.of(
                "prisoner", "PRISONER", "executioner", "EXECUTIONER",
                "pawn", "PAWN", "colonist", "COLONIST", "attacker", "ATTACKER",
                "victim", "VICTIM", "studier", "STUDIER", "entity", "ENTITY");
        String alias = aliases.get(prefix.toLowerCase(Locale.ROOT));
        if (alias != null) {
            values.add(alias);
        }
        return List.copyOf(values);
    }

    private Map<String, Object> findArtStyle(String styleId, String language) {
        if (styleId == null || styleId.isBlank() || "auto".equalsIgnoreCase(styleId)) {
            return null;
        }
        String lang = normalizeLanguage(language);
        for (TynanTextCompositionManager.StyleOption option : artStyles(lang)) {
            String localized = "zh".equals(lang) ? option.chinese() : option.english();
            if (!styleId.equals(option.id()) && !styleId.equals(localized)) {
                continue;
            }
            Map<String, Object> style = new LinkedHashMap<>();
            style.put("id", option.id());
            style.put(lang, localized);
            return style;
        }
        return null;
    }

    private List<TynanTextCompositionManager.StyleClauseOption> filterStyleClauses(
            List<TynanTextCompositionManager.StyleClauseOption> all, List<String> selectedIds) {
        if (selectedIds.isEmpty()) {
            return all;
        }
        Set<String> allowed = Set.copyOf(selectedIds);
        List<TynanTextCompositionManager.StyleClauseOption> filtered = all.stream()
                .filter(option -> allowed.contains(option.id()))
                .toList();
        return filtered.isEmpty() ? all : filtered;
    }

    private String repairTaleNoun(String taleNoun, Map<String, String> context, String language) {
        if (taleNoun.isBlank()) {
            return taleNoun;
        }
        boolean needsSubject = taleNoun.startsWith("的")
                || taleNoun.equals("死刑") || taleNoun.equals("末日");
        if (!needsSubject) {
            return taleNoun.replaceFirst("^的+", "");
        }
        String prisoner = fallback(
                lookupPersonField(context, "PRISONER", "nameDef"),
                lookupPersonField(context, "prisoner", "nameDef"));
        String executioner = fallback(
                lookupPersonField(context, "EXECUTIONER", "nameDef"),
                lookupPersonField(context, "executioner", "nameDef"));
        if (!prisoner.isBlank() && !executioner.isBlank()) {
            return "zh".equals(language)
                    ? executioner + "处决" + prisoner
                    : executioner + " executing " + prisoner;
        }
        if (!prisoner.isBlank()) {
            return "zh".equals(language) ? prisoner + "的死刑" : "the execution of " + prisoner;
        }
        return taleNoun.replaceFirst("^的+", "");
    }

    private Map<String, Object> dataRulePack(String id) {
        return map(map(data.get("rulepacks")).get(id));
    }

    private List<Map<String, Object>> rawRules(String packId, String keyword) {
        Map<String, Object> pack = dataRulePack(packId);
        return maps(map(map(pack.get("rulesByKeyword")).get(keyword)));
    }

    private List<WeightedText> ruleLists(Map<String, Object> pack, String keyword, String language) {
        List<WeightedText> result = new ArrayList<>();
        for (Map<String, Object> rule : maps(map(pack.get("rulesByKeyword")).get(keyword))) {
            String en = string(rule.get("en"));
            String zh = string(rule.get("zh"));
            result.add(new WeightedText(
                    "en".equals(language) ? fallback(en, zh) : fallback(zh, en),
                    decimal(rule.get("weight"), 1.0D)));
        }
        return result;
    }

    private void addPrefixFromToken(Set<String> prefixes, String token) {
        if (token == null) {
            return;
        }
        int separator = token.indexOf('_');
        if (separator > 0) {
            addPrefix(prefixes, token.substring(0, separator));
        }
    }

    private void addPrefix(Set<String> prefixes, String prefix) {
        if (prefix != null && !prefix.isBlank()) {
            prefixes.add(prefix);
        }
    }

    private static String pick(List<WeightedText> values, Random random) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        double totalWeight = values.stream().mapToDouble(TynanTextComposer::effectiveWeight).sum();
        double selected = random.nextDouble() * totalWeight;
        for (WeightedText value : values) {
            selected -= effectiveWeight(value);
            if (selected <= 0.0D) {
                return value.text();
            }
        }
        return values.get(values.size() - 1).text();
    }

    private static double effectiveWeight(WeightedText value) {
        return Double.isFinite(value.weight()) && value.weight() > 0.0D ? value.weight() : 1.0D;
    }

    private static Map<String, List<Map<String, Object>>> mapOfLists(Map<String, Object> values) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, maps(value)));
        return result;
    }

    private static String normalizeSpaces(String text, String language) {
        if ("zh".equals(language)) {
            return text.replaceAll("\\s+", "")
                    .replaceAll("，+", "，")
                    .replaceAll("。+", "。")
                    .trim();
        }
        return text.replaceAll("\\s+", " ")
                .replaceAll("\\s+([,.!?;:])", "$1")
                .trim();
    }

    private static String finishSentence(String text, String language) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String result = text.trim();
        if ("zh".equals(language)) {
            result = result.replaceAll("[，、；:\\s]+$", "");
            return result.matches(".*[。！？…]$") ? result : result + "。";
        }
        result = result.replaceAll("[,;:\\s]+$", "");
        return result.matches(".*[.!?…]$") ? result : result + ".";
    }

    private static String joinSentences(List<String> parts, String language) {
        List<String> sentences = parts.stream()
                .map(value -> finishSentence(value, language))
                .filter(value -> !value.isBlank())
                .toList();
        return String.join("zh".equals(language) ? "" : " ", sentences);
    }

    private static String polishText(String text, String language) {
        if ("zh".equals(language)) {
            return text.replaceAll("\\[[A-Za-z0-9_]+]", "")
                    .replaceAll("(呈现|气息|对比|布局|构图|方法|风格|补充)(?=(这件|本[件幅]|展示|描绘|刻画|表现|描述|塑造|作品|画面))", "$1。")
                    .replaceAll("([入来去正])(?=这件)", "$1。")
                    .replaceAll("(格格不入)(?=[^。！？])", "$1。")
                    .replaceAll("这件(?:展示|描绘|刻画|表现|描述|塑造)(描述了|记述了|讲述了|引述了)", "这件作品$1")
                    .replaceAll("(?:展示|描绘|刻画|表现|描述|塑造)(记述了|描述了|讲述了)", "作品$1")
                    .replaceAll("(?:展示|描绘|刻画|表现|描述|塑造)中所引述", "作品中所引述")
                    .replaceAll("(展示|描绘|刻画|表现|描述|塑造)(展示|描绘|刻画|表现|描述|塑造)", "$1")
                    .replaceAll("描述描述", "描述")
                    .replaceAll("记述记述", "记述")
                    .replaceAll("描绘描绘", "描绘")
                    .replaceAll("讲述了的(?=\\S)", "讲述了")
                    .replaceAll("记述了的(?=\\S)", "记述了")
                    .replaceAll("描述了的(?=\\S)", "描述了")
                    .replaceAll("关于的(?=\\S)", "关于")
                    .replaceAll("讲述了(死刑|末日|处决)(?=[，。]|$)", "讲述了一场$1")
                    .replaceAll("记述了(死刑|末日|处决)(?=[，。]|$)", "记述了一场$1")
                    .replaceAll("描述了(死刑|末日|处决)(?=[，。]|$)", "描述了一场$1")
                    .replaceAll("发生在([^，。的]+)的的", "发生在$1的")
                    .replaceAll("的的+", "的")
                    .replaceAll("在在+", "在")
                    .replaceAll("([了着过])的(?=[^的，。\\s]{0,6}(?:死刑|末日|处决|故事|往事))", "$1")
                    .replace("讲述了。", "")
                    .replace("记述了。", "")
                    .replace("描述了。", "")
                    .replaceAll("，{2,}", "，")
                    .replaceAll("。{2,}", "。")
                    .replace("，。", "。")
                    .replace("。，", "。")
                    .replaceAll("([。！？])[，、；]+", "$1")
                    .replaceAll("^[，、。；]+", "")
                    .replaceAll("\\s+", "")
                    .trim();
        }
        return text.replaceAll("\\[[A-Za-z0-9_]+]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("\\s+([,.!?;:])", "$1")
                .replaceAll("([.!?]){2,}", "$1")
                .replaceAll("(?i)\\bThis\\s+(image|portrayal|representation|illustration|artwork)\\b", "This work")
                .trim();
    }

    private static String localized(Map<String, Object> values, String language, String fallback) {
        return fallback(string(values.get(language)), fallback);
    }

    private static String fallback(String first, String second) {
        return first == null || first.isBlank() ? (second == null ? "" : second) : first;
    }

    private static String normalizeLanguage(String language) {
        return "en".equalsIgnoreCase(language) ? "en" : "zh";
    }

    private static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double decimal(Object value, double fallback) {
        return value instanceof Number number && Double.isFinite(number.doubleValue())
                ? number.doubleValue() : fallback;
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(TynanTextComposer::string).toList();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static List<Map<String, Object>> maps(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list(value)) {
            Map<String, Object> map = map(item);
            if (!map.isEmpty()) {
                result.add(map);
            }
        }
        return result;
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private static Map<String, Object> findById(List<?> values, String id) {
        for (Object value : values) {
            Map<String, Object> item = map(value);
            if (id.equals(item.get("id"))) {
                return item;
            }
        }
        return null;
    }

    private static String styleLabel(int index, String language) {
        String[] labels = "en".equals(language) ? STYLE_LABELS_EN : STYLE_LABELS_ZH;
        return index >= 0 && index < labels.length
                ? labels[index] : ("en".equals(language) ? "Style clause " : "风格句 ") + (index + 1);
    }

    private static boolean hasTaleRules(Map<String, Object> tale) {
        Map<String, Object> rules = map(tale.get("rulesByKeyword"));
        return !maps(rules.get("image")).isEmpty() || !maps(rules.get("tale_noun")).isEmpty();
    }

    private static long hashSeed(String value) {
        long hash = 2166136261L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash = (hash * 16777619L) & 0xffffffffL;
        }
        return hash;
    }

    private static Map<String, Object> toStringMap(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (section == null) {
            return result;
        }
        for (String key : section.getKeys(false)) {
            result.put(key, convertValue(section.get(key)));
        }
        return result;
    }

    private static Object convertValue(Object value) {
        if (value instanceof ConfigurationSection section) {
            return toStringMap(section);
        }
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> result = new LinkedHashMap<>();
            source.forEach((key, item) -> result.put(String.valueOf(key), convertValue(item)));
            return result;
        }
        if (value instanceof List<?> source) {
            return source.stream().map(TynanTextComposer::convertValue).toList();
        }
        return value;
    }

    private record WeightedText(String text, double weight) {
        private WeightedText {
            text = text == null ? "" : text;
        }
    }

    private record BiText(String en, String zh, double weight) {
        private BiText {
            en = en == null ? "" : en;
            zh = zh == null ? "" : zh;
        }
    }
}
