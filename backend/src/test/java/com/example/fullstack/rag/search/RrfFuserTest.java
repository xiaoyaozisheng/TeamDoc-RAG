package com.example.fullstack.rag.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** RRF 融合纯函数单测（RET-02）：score = Σ 1/(k + rank_i)，两路命中互相补位。 */
class RrfFuserTest {

    private ScoredChunk hit(long id, Double vectorScore, Double keywordScore) {
        return new ScoredChunk(id, 1L, "doc.txt", 1, "内容" + id, vectorScore, keywordScore, 0);
    }

    @Test
    void itemRankedFirstInBothListsGetsHighestScore() {
        List<ScoredChunk> fused = RrfFuser.fuse(
                List.of(hit(1, 0.9, null), hit(2, 0.8, null)),
                List.of(hit(1, null, 5.0), hit(3, null, 4.0)),
                60, 5);

        assertThat(fused.get(0).chunkId()).isEqualTo(1L);
        // 两路第 1 名: 1/61 + 1/61
        assertThat(fused.get(0).rrfScore()).isCloseTo(2.0 / 61, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void keywordOnlyHitStillEntersFusion() {
        List<ScoredChunk> fused = RrfFuser.fuse(
                List.of(hit(1, 0.9, null)),
                List.of(hit(2, null, 3.0)),
                60, 5);

        assertThat(fused).hasSize(2);
        assertThat(fused.get(0).chunkId()).isEqualTo(1L);
        assertThat(fused.get(1).chunkId()).isEqualTo(2L);
        assertThat(fused.get(1).vectorScore()).isNull();
        assertThat(fused.get(1).rrfScore()).isCloseTo(1.0 / 61, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void topKIsRespected() {
        List<ScoredChunk> vectorHits = List.of(hit(1, 0.9, null), hit(2, 0.8, null), hit(3, 0.7, null));
        List<ScoredChunk> fused = RrfFuser.fuse(vectorHits, List.of(), 60, 2);
        assertThat(fused).hasSize(2);
    }

    @Test
    void emptyInputsProduceEmptyResult() {
        assertThat(RrfFuser.fuse(List.of(), List.of(), 60, 5)).isEmpty();
    }
}
