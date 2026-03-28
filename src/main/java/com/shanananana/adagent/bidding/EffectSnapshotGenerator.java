package com.shanananana.adagent.bidding;

import com.shanananana.adagent.bidding.dto.EffectSnapshotFile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * <strong>合成效果快照</strong>生成器（演示用）：按与 {@link BidStrategyRepository} 一致的维度网格随机生成展示/点击/ROI 等，
 * 使无真实数仓时仍可为 {@link BidCoefficientLlmService} 提供可区分的输入；可选 {@code campaignId} 作种子以区分计划。
 */
@Component
public class EffectSnapshotGenerator {

    public EffectSnapshotFile generateFreshSnapshot() {
        return generateFreshSnapshot(null);
    }

    /**
     * @param campaignId 非空时混入随机种子，使各计划合成快照不同；调价数据仍按人群×时段×设备网格。
     */
    public EffectSnapshotFile generateFreshSnapshot(String campaignId) {
        EffectSnapshotFile f = new EffectSnapshotFile();
        f.setVersion("1.0");
        String now = Instant.now().toString();
        f.setWindowEnd(now);
        f.setGeneratedAt(now);
        long seed = System.nanoTime();
        if (campaignId != null && !campaignId.isBlank()) {
            seed ^= ((long) campaignId.hashCode()) << 32;
        }
        List<EffectSnapshotFile.EffectSnapshotEntry> entries = new ArrayList<>();
        for (String aud : BidStrategyRepository.dimensionAudiences()) {
            for (String dev : BidStrategyRepository.dimensionDevices()) {
                for (int h : BidStrategyRepository.dimensionHourSlots()) {
                    EffectSnapshotFile.EffectSnapshotEntry e = new EffectSnapshotFile.EffectSnapshotEntry();
                    e.setAudience(aud);
                    e.setDevice(dev);
                    e.setHourSlot(h);
                    seed = seed * 6364136223846793005L + 1;
                    long imp = 800 + Math.abs(seed % 8000);
                    long clk = Math.max(1, imp * (3 + Math.abs((int) (seed >> 8) % 12)) / 200);
                    double cost = Math.round(clk * (0.4 + (Math.abs((int) (seed >> 16) % 12)) / 25.0) * 100) / 100.0;
                    double ctr = imp > 0 ? (double) clk / imp : 0;
                    double roi = 0.45 + (Math.abs((int) (seed >> 24) % 20)) / 25.0;
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
}
