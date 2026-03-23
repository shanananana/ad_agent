package com.example.adagent.bidding;

import com.example.adagent.bidding.dto.BaseBidModelFile;
import com.example.adagent.bidding.dto.CoefficientJobLogFile;
import com.example.adagent.bidding.dto.CoefficientsFile;
import com.example.adagent.bidding.dto.EffectSnapshotFile;
import com.example.adagent.config.DataPathConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <strong>出价策略与调价产物</strong>的 JSON 持久化：读写全局或按计划的 {@code base_bid_model.json}、
 * {@code coefficients.json}、{@code effect_snapshot.json}、{@code coefficient_job_log.json}，含文件锁与初始化逻辑。
 */
@Repository
public class BidStrategyRepository {

    private static final Logger logger = LoggerFactory.getLogger(BidStrategyRepository.class);
    private static final String[] AUDIENCES = {"18-24", "25-34", "35-44"};
    private static final String[] DEVICES = {"ios", "android", "web"};
    private static final int[] HOUR_SLOTS = {0, 6, 12, 18, 22};

    private final DataPathConfig dataPathConfig;
    private final ObjectMapper objectMapper;
    private final ReentrantLock lock = new ReentrantLock();

    public BidStrategyRepository(DataPathConfig dataPathConfig, ObjectMapper objectMapper) {
        this.dataPathConfig = dataPathConfig;
        this.objectMapper = objectMapper != null ? objectMapper.copy() : new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    public void ensureBidDirAndDefaults() throws IOException {
        Path dir = dataPathConfig.getBidStrategyDir();
        Files.createDirectories(dir);
        if (!Files.exists(dataPathConfig.getBidBaseModelPath())) {
            writeBaseModel(defaultBaseModel());
            logger.info("【出价策略】已初始化 base_bid_model.json");
        }
        if (!Files.exists(dataPathConfig.getBidCoefficientsPath())) {
            writeCoefficients(defaultCoefficients());
            logger.info("【出价策略】已初始化 coefficients.json");
        }
        if (!Files.exists(dataPathConfig.getBidEffectSnapshotPath())) {
            writeEffectSnapshot(defaultEffectSnapshot());
            logger.info("【出价策略】已初始化 effect_snapshot.json");
        }
        if (!Files.exists(dataPathConfig.getBidCoefficientJobLogPath())) {
            CoefficientJobLogFile log = new CoefficientJobLogFile();
            log.setJobs(new ArrayList<>());
            writeJobLog(log);
            logger.info("【出价策略】已初始化 coefficient_job_log.json");
        }
    }

    public static boolean isGlobalBidScope(String campaignId) {
        return campaignId == null || campaignId.isBlank();
    }

    private Path resolveBaseModelPath(String campaignId) {
        if (isGlobalBidScope(campaignId)) {
            return dataPathConfig.getBidBaseModelPath();
        }
        return dataPathConfig.getBidCampaignBaseModelPath(campaignId);
    }

    private Path resolveCoefficientsPath(String campaignId) {
        if (isGlobalBidScope(campaignId)) {
            return dataPathConfig.getBidCoefficientsPath();
        }
        return dataPathConfig.getBidCampaignCoefficientsPath(campaignId);
    }

    private Path resolveEffectSnapshotPath(String campaignId) {
        if (isGlobalBidScope(campaignId)) {
            return dataPathConfig.getBidEffectSnapshotPath();
        }
        return dataPathConfig.getBidCampaignEffectSnapshotPath(campaignId);
    }

    private Path resolveJobLogPath(String campaignId) {
        if (isGlobalBidScope(campaignId)) {
            return dataPathConfig.getBidCoefficientJobLogPath();
        }
        return dataPathConfig.getBidCampaignJobLogPath(campaignId);
    }

    /** 单计划目录下缺省文件时写入默认 B/α/快照/空日志（调用方需已持有 lock） */
    private void ensureCampaignBidReadyUnlocked(String campaignId) throws IOException {
        if (isGlobalBidScope(campaignId)) {
            return;
        }
        Files.createDirectories(dataPathConfig.getBidCampaignDir(campaignId));
        Path base = dataPathConfig.getBidCampaignBaseModelPath(campaignId);
        if (!Files.exists(base)) {
            objectMapper.writeValue(base.toFile(), defaultBaseModel());
            logger.info("【出价策略】已初始化计划 {} 的 base_bid_model.json", campaignId);
        }
        Path coeff = dataPathConfig.getBidCampaignCoefficientsPath(campaignId);
        if (!Files.exists(coeff)) {
            objectMapper.writeValue(coeff.toFile(), defaultCoefficients());
        }
        Path snap = dataPathConfig.getBidCampaignEffectSnapshotPath(campaignId);
        if (!Files.exists(snap)) {
            objectMapper.writeValue(snap.toFile(), defaultEffectSnapshot());
        }
        Path log = dataPathConfig.getBidCampaignJobLogPath(campaignId);
        if (!Files.exists(log)) {
            CoefficientJobLogFile logFile = new CoefficientJobLogFile();
            logFile.setJobs(new ArrayList<>());
            objectMapper.writeValue(log.toFile(), logFile);
        }
    }

    public BaseBidModelFile loadBaseModel() {
        return loadBaseModel(null);
    }

    public BaseBidModelFile loadBaseModel(String campaignId) {
        lock.lock();
        try {
            if (!isGlobalBidScope(campaignId)) {
                ensureCampaignBidReadyUnlocked(campaignId);
            }
            Path p = resolveBaseModelPath(campaignId);
            if (!Files.exists(p)) {
                return defaultBaseModel();
            }
            return objectMapper.readValue(p.toFile(), BaseBidModelFile.class);
        } catch (Exception e) {
            logger.warn("【出价策略】读取 base_bid_model 失败: {}", e.getMessage());
            return defaultBaseModel();
        } finally {
            lock.unlock();
        }
    }

    public void writeBaseModel(BaseBidModelFile file) throws IOException {
        writeBaseModel(file, null);
    }

    public void writeBaseModel(BaseBidModelFile file, String campaignId) throws IOException {
        lock.lock();
        try {
            Path p = resolveBaseModelPath(campaignId);
            Files.createDirectories(p.getParent());
            objectMapper.writeValue(p.toFile(), file);
        } finally {
            lock.unlock();
        }
    }

    public CoefficientsFile loadCoefficients() {
        return loadCoefficients(null);
    }

    public CoefficientsFile loadCoefficients(String campaignId) {
        lock.lock();
        try {
            if (!isGlobalBidScope(campaignId)) {
                ensureCampaignBidReadyUnlocked(campaignId);
            }
            Path p = resolveCoefficientsPath(campaignId);
            if (!Files.exists(p)) {
                return defaultCoefficients();
            }
            return objectMapper.readValue(p.toFile(), CoefficientsFile.class);
        } catch (Exception e) {
            logger.warn("【出价策略】读取 coefficients 失败: {}", e.getMessage());
            return defaultCoefficients();
        } finally {
            lock.unlock();
        }
    }

    public void writeCoefficients(CoefficientsFile file) throws IOException {
        writeCoefficients(file, null);
    }

    public void writeCoefficients(CoefficientsFile file, String campaignId) throws IOException {
        lock.lock();
        try {
            Path p = resolveCoefficientsPath(campaignId);
            Files.createDirectories(p.getParent());
            objectMapper.writeValue(p.toFile(), file);
        } finally {
            lock.unlock();
        }
    }

    public EffectSnapshotFile loadEffectSnapshot() {
        return loadEffectSnapshot(null);
    }

    public EffectSnapshotFile loadEffectSnapshot(String campaignId) {
        lock.lock();
        try {
            if (!isGlobalBidScope(campaignId)) {
                ensureCampaignBidReadyUnlocked(campaignId);
            }
            Path p = resolveEffectSnapshotPath(campaignId);
            if (!Files.exists(p)) {
                return defaultEffectSnapshot();
            }
            return objectMapper.readValue(p.toFile(), EffectSnapshotFile.class);
        } catch (Exception e) {
            logger.warn("【出价策略】读取 effect_snapshot 失败: {}", e.getMessage());
            return defaultEffectSnapshot();
        } finally {
            lock.unlock();
        }
    }

    public void writeEffectSnapshot(EffectSnapshotFile file) throws IOException {
        writeEffectSnapshot(file, null);
    }

    public void writeEffectSnapshot(EffectSnapshotFile file, String campaignId) throws IOException {
        lock.lock();
        try {
            Path p = resolveEffectSnapshotPath(campaignId);
            Files.createDirectories(p.getParent());
            objectMapper.writeValue(p.toFile(), file);
        } finally {
            lock.unlock();
        }
    }

    public CoefficientJobLogFile loadJobLog() {
        return loadJobLog(null);
    }

    public CoefficientJobLogFile loadJobLog(String campaignId) {
        lock.lock();
        try {
            if (!isGlobalBidScope(campaignId)) {
                ensureCampaignBidReadyUnlocked(campaignId);
            }
            Path p = resolveJobLogPath(campaignId);
            if (!Files.exists(p)) {
                CoefficientJobLogFile empty = new CoefficientJobLogFile();
                empty.setJobs(new ArrayList<>());
                return empty;
            }
            CoefficientJobLogFile f = objectMapper.readValue(p.toFile(), CoefficientJobLogFile.class);
            if (f.getJobs() == null) {
                f.setJobs(new ArrayList<>());
            }
            return f;
        } catch (Exception e) {
            logger.warn("【出价策略】读取 job_log 失败: {}", e.getMessage());
            CoefficientJobLogFile empty = new CoefficientJobLogFile();
            empty.setJobs(new ArrayList<>());
            return empty;
        } finally {
            lock.unlock();
        }
    }

    public void writeJobLog(CoefficientJobLogFile file) throws IOException {
        writeJobLog(file, null);
    }

    public void writeJobLog(CoefficientJobLogFile file, String campaignId) throws IOException {
        lock.lock();
        try {
            Path p = resolveJobLogPath(campaignId);
            Files.createDirectories(p.getParent());
            objectMapper.writeValue(p.toFile(), file);
        } finally {
            lock.unlock();
        }
    }

    public static BaseBidModelFile defaultBaseModel() {
        BaseBidModelFile f = new BaseBidModelFile();
        f.setVersion("1.0");
        f.setUpdatedAt(Instant.now().toString());
        List<BaseBidModelFile.BaseBidEntry> entries = new ArrayList<>();
        int i = 0;
        for (String aud : AUDIENCES) {
            for (String dev : DEVICES) {
                for (int h : HOUR_SLOTS) {
                    BaseBidModelFile.BaseBidEntry e = new BaseBidModelFile.BaseBidEntry();
                    e.setAudience(aud);
                    e.setDevice(dev);
                    e.setHourSlot(h);
                    double factor = 1.0 + (i % 5) * 0.05;
                    e.setBaseBid(Math.round(10.0 * factor * 10) / 10.0);
                    entries.add(e);
                    i++;
                }
            }
        }
        f.setEntries(entries);
        return f;
    }

    public static CoefficientsFile defaultCoefficients() {
        CoefficientsFile f = new CoefficientsFile();
        f.setVersion(1L);
        f.setUpdatedAt(Instant.now().toString());
        List<CoefficientsFile.CoefficientEntry> entries = new ArrayList<>();
        for (String aud : AUDIENCES) {
            for (String dev : DEVICES) {
                for (int h : HOUR_SLOTS) {
                    CoefficientsFile.CoefficientEntry e = new CoefficientsFile.CoefficientEntry();
                    e.setAudience(aud);
                    e.setDevice(dev);
                    e.setHourSlot(h);
                    e.setAlpha(1.0);
                    entries.add(e);
                }
            }
        }
        f.setEntries(entries);
        return f;
    }

    public static EffectSnapshotFile defaultEffectSnapshot() {
        EffectSnapshotFile f = new EffectSnapshotFile();
        f.setVersion("1.0");
        f.setWindowEnd(Instant.now().toString());
        f.setGeneratedAt(Instant.now().toString());
        List<EffectSnapshotFile.EffectSnapshotEntry> entries = new ArrayList<>();
        int seed = 7;
        for (String aud : AUDIENCES) {
            for (String dev : DEVICES) {
                for (int h : HOUR_SLOTS) {
                    EffectSnapshotFile.EffectSnapshotEntry e = new EffectSnapshotFile.EffectSnapshotEntry();
                    e.setAudience(aud);
                    e.setDevice(dev);
                    e.setHourSlot(h);
                    seed = seed * 31 + aud.hashCode() + dev.hashCode() + h;
                    long imp = 1000 + Math.abs(seed % 5000);
                    long clk = Math.max(1, imp * (5 + Math.abs(seed % 8)) / 200);
                    double cost = Math.round(clk * (0.5 + (Math.abs(seed) % 10) / 20.0) * 100) / 100.0;
                    double ctr = imp > 0 ? (double) clk / imp : 0;
                    double roi = 0.6 + (Math.abs(seed) % 15) / 30.0;
                    e.setImpressions(imp);
                    e.setClicks(clk);
                    e.setCost(cost);
                    e.setCtr(Math.round(ctr * 10000) / 10000.0);
                    e.setRoi(Math.round(roi * 100) / 100.0);
                    entries.add(e);
                }
            }
        }
        f.setEntries(entries);
        return f;
    }

    public static List<String> dimensionAudiences() {
        return List.of(AUDIENCES);
    }

    public static List<String> dimensionDevices() {
        return List.of(DEVICES);
    }

    public static int[] dimensionHourSlots() {
        return HOUR_SLOTS.clone();
    }
}
