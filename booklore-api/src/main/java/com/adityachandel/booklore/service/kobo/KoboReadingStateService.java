package com.adityachandel.booklore.service.kobo;

import com.adityachandel.booklore.mapper.KoboReadingStateMapper;
import com.adityachandel.booklore.model.dto.kobo.KoboReadingState;
import com.adityachandel.booklore.model.dto.kobo.KoboReadingStateWrapper;
import com.adityachandel.booklore.model.dto.response.kobo.KoboReadingStateResponse;
import com.adityachandel.booklore.model.entity.KoboReadingStateEntity;
import com.adityachandel.booklore.repository.KoboReadingStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KoboReadingStateService {

    private final KoboReadingStateRepository repository;
    private final KoboReadingStateMapper mapper;

    public KoboReadingStateResponse saveReadingState(List<KoboReadingState> readingStates) {
        List<KoboReadingState> koboReadingStates = saveAll(readingStates);

        List<KoboReadingStateResponse.UpdateResult> updateResults = koboReadingStates.stream()
                .map(state -> KoboReadingStateResponse.UpdateResult.builder()
                        .entitlementId(state.getEntitlementId())
                        .currentBookmarkResult(KoboReadingStateResponse.Result.success())
                        .statisticsResult(KoboReadingStateResponse.Result.success())
                        .statusInfoResult(KoboReadingStateResponse.Result.success())
                        .build())
                .collect(Collectors.toList());

        return KoboReadingStateResponse.builder()
                .requestResult("Success")
                .updateResults(updateResults)
                .build();
    }

    private List<KoboReadingState> saveAll(List<KoboReadingState> dtos) {
        return dtos.stream()
                .map(dto -> {
                    KoboReadingStateEntity entity = repository.findByEntitlementId(dto.getEntitlementId())
                            .map(existing -> {
                                existing.setCurrentBookmarkJson(mapper.toJson(dto.getCurrentBookmark()));
                                existing.setStatisticsJson(mapper.toJson(dto.getStatistics()));
                                existing.setStatusInfoJson(mapper.toJson(dto.getStatusInfo()));
                                existing.setLastModifiedString(mapper.cleanString(String.valueOf(dto.getLastModified())));
                                return existing;
                            })
                            .orElseGet(() -> {
                                KoboReadingStateEntity newEntity = mapper.toEntity(dto);
                                newEntity.setCreated(mapper.cleanString(String.valueOf(dto.getCreated())));
                                return newEntity;
                            });

                    return repository.save(entity);
                })
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    public KoboReadingStateWrapper getReadingState(String entitlementId) {
        Optional<KoboReadingState> readingState = repository.findByEntitlementId(entitlementId).map(mapper::toDto);
        return readingState.map(state -> KoboReadingStateWrapper.builder()
                .readingStates(List.of(state))
                .build()).orElse(null);
    }
}