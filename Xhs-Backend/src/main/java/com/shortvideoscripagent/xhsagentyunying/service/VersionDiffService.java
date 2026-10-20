package com.shortvideoscripagent.xhsagentyunying.service;

import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.domain.entity.ContentAssetVersion;
import com.shortvideoscripagent.xhsagentyunying.domain.mapper.ContentAssetVersionMapper;
import com.shortvideoscripagent.xhsagentyunying.dto.asset.VersionDiffResponse;
import com.shortvideoscripagent.xhsagentyunying.dto.asset.VersionDiffResponse.DiffChunk;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceGuards;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class VersionDiffService {

    private static final int CODE_NOT_FOUND = 40430;
    private static final int CODE_BAD_REQUEST = 40030;

    private final ContentAssetVersionMapper versionMapper;

    /**
     * 对比两个版本，输出结构化 diff 结果。
     * @param assetId 资产 ID
     * @param v1      版本号 1（旧版）
     * @param v2      版本号 2（新版）
     * @return 包含 chunks（按顺序排列的 diff 块）和统计摘要
     */
    public VersionDiffResponse diff(UUID assetId, int v1, int v2) {
        if (v1 == v2) {
            throw new BusinessException(CODE_BAD_REQUEST, "v1_and_v2_must_differ");
        }

        ContentAssetVersion a = versionMapper.selectByAssetIdAndVersion(assetId, v1)
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND, "version_not_found"));
        ContentAssetVersion b = versionMapper.selectByAssetIdAndVersion(assetId, v2)
                .orElseThrow(() -> new BusinessException(CODE_NOT_FOUND, "version_not_found"));

        // 读取正文
        String textA = a.getBody() != null ? a.getBody() : "";
        String textB = b.getBody() != null ? b.getBody() : "";

        // LCS-based line diff
        List<DiffChunk> chunks = computeLcsDiff(textA, textB);

        // 统计
        long added = chunks.stream().filter(c -> "added".equals(c.type())).count();
        long removed = chunks.stream().filter(c -> "removed".equals(c.type())).count();
        long unchanged = chunks.stream().filter(c -> "unchanged".equals(c.type())).count();

        // 摘要：最多返回变更的前 3 行
        List<String> changeSummary = new ArrayList<>();
        for (DiffChunk c : chunks) {
            if ("added".equals(c.type()) || "removed".equals(c.type())) {
                String prefix = "added".equals(c.type()) ? "+ " : "- ";
                changeSummary.add(prefix + c.content());
                if (changeSummary.size() >= 3) break;
            }
        }

        return VersionDiffResponse.builder()
                .assetId(assetId.toString())
                .versionA(v1)
                .versionB(v2)
                .chunks(chunks)
                .stats(VersionDiffResponse.DiffStats.builder()
                        .addedLines(added)
                        .removedLines(removed)
                        .unchangedLines(unchanged)
                        .build())
                .changeSummary(changeSummary)
                .build();
    }

    /**
     * LCS-based O(ND) diff algorithm。
     * 返回按原始顺序排列的 diff 块。
     */
    private List<DiffChunk> computeLcsDiff(String textA, String textB) {
        String[] linesA = textA.isEmpty() ? new String[0] : textA.split("\\r?\\n", -1);
        String[] linesB = textB.isEmpty() ? new String[0] : textB.split("\\r?\\n", -1);

        int m = linesA.length;
        int n = linesB.length;

        // LCS DP table
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (linesA[i - 1].equals(linesB[j - 1])) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        // Backtrack to build diff
        List<DiffChunk> chunks = new ArrayList<>();
        int i = m, j = n;
        Deque<DiffChunk> stack = new ArrayDeque<>();

        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && linesA[i - 1].equals(linesB[j - 1])) {
                stack.push(new DiffChunk("unchanged", linesA[i - 1]));
                i--; j--;
            } else if (j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                stack.push(new DiffChunk("added", linesB[j - 1]));
                j--;
            } else {
                stack.push(new DiffChunk("removed", linesA[i - 1]));
                i--;
            }
        }

        while (!stack.isEmpty()) {
            chunks.add(stack.pollFirst());
        }
        return chunks;
    }
}
