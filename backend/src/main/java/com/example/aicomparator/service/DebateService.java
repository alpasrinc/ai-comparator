package com.example.aicomparator.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aicomparator.dto.DebateDetailResponse;
import com.example.aicomparator.dto.DebateMessageResponse;
import com.example.aicomparator.dto.DebateRequest;
import com.example.aicomparator.dto.DebateSummaryResponse;
import com.example.aicomparator.dto.TokenUsage;
import com.example.aicomparator.entity.AiProviderType;
import com.example.aicomparator.entity.Debate;
import com.example.aicomparator.entity.DebateMessage;
import com.example.aicomparator.entity.DebateMessageRole;
import com.example.aicomparator.repository.DebateMessageRepository;
import com.example.aicomparator.repository.DebateRepository;

@Service
public class DebateService {

    private final DebateRepository debateRepository;
    private final DebateMessageRepository debateMessageRepository;

    public DebateService(
            DebateRepository debateRepository,
            DebateMessageRepository debateMessageRepository
    ) {
        this.debateRepository = debateRepository;
        this.debateMessageRepository = debateMessageRepository;
    }

    @Transactional
    public Long createDebate(DebateRequest request) {
        Set<AiProviderType> participants =
                new LinkedHashSet<>(request.participants());

        Debate debate = new Debate(
                request.topic(),
                request.rounds(),
                request.synthesizer(),
                participants
        );

        return debateRepository.save(debate).getId();
    }

    @Transactional
    public Long saveParticipantMessage(
            Long debateId,
            int round,
            AiProviderType provider,
            String content
    ) {
        return saveParticipantMessage(
                debateId, round, provider, content, TokenUsage.EMPTY);
    }

    @Transactional
    public Long saveParticipantMessage(
            Long debateId,
            int round,
            AiProviderType provider,
            String content,
            TokenUsage usage
    ) {
        Debate debate = requireDebate(debateId);
        DebateMessage message = debateMessageRepository.save(
                DebateMessage.participant(debate, round, provider, content,
                        usage.inputTokens(), usage.outputTokens())
        );
        return message.getId();
    }

    @Transactional
    public Long saveSynthesisMessage(
            Long debateId,
            AiProviderType provider,
            String content
    ) {
        return saveSynthesisMessage(
                debateId, provider, content, TokenUsage.EMPTY);
    }

    @Transactional
    public Long saveSynthesisMessage(
            Long debateId,
            AiProviderType provider,
            String content,
            TokenUsage usage
    ) {
        Debate debate = requireDebate(debateId);
        DebateMessage message = debateMessageRepository.save(
                DebateMessage.synthesis(debate, provider, content,
                        usage.inputTokens(), usage.outputTokens())
        );
        debate.complete();
        debateRepository.save(debate);
        return message.getId();
    }

    @Transactional
    public void markCompletedWithoutSynthesis(Long debateId) {
        Debate debate = requireDebate(debateId);
        debate.completeWithoutSynthesis();
        debateRepository.save(debate);
    }

    @Transactional
    public void markFailed(Long debateId) {
        Debate debate = requireDebate(debateId);
        debate.fail();
        debateRepository.save(debate);
    }

    @Transactional(readOnly = true)
    public List<DebateSummaryResponse> listDebates() {
        return debateRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(debate -> new DebateSummaryResponse(
                        debate.getId(),
                        debate.getTopic(),
                        debate.getRounds(),
                        debate.getStatus().name(),
                        debate.getUpdatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public DebateDetailResponse getDebate(Long debateId) {
        Debate debate = requireDebate(debateId);

        List<DebateMessage> storedMessages = debateMessageRepository
                .findByDebateIdOrderByIdAsc(debateId);

        List<DebateMessageResponse> messages = storedMessages.stream()
                        .map(message -> new DebateMessageResponse(
                                message.getId(),
                                message.getRoundNumber(),
                                message.getProvider().name(),
                                message.getRole().name(),
                                message.getContent(),
                                new TokenUsage(
                                        message.getInputTokens() == null
                                                ? 0 : message.getInputTokens(),
                                        message.getOutputTokens() == null
                                                ? 0 : message.getOutputTokens())
                        ))
                .toList();

        String finalAnswer = storedMessages.stream()
                .filter(message ->
                        message.getRole() == DebateMessageRole.SYNTHESIS)
                .map(DebateMessage::getContent)
                .reduce((previous, current) -> current)
                .orElse(null);

        List<String> participants = debate.getParticipants().stream()
                .map(AiProviderType::name)
                .collect(Collectors.toList());

        return new DebateDetailResponse(
                debate.getId(),
                debate.getTopic(),
                debate.getRounds(),
                participants,
                debate.getSynthesizerProvider().name(),
                debate.getStatus().name(),
                finalAnswer,
                messages
        );
    }

    @Transactional
    public void deleteDebate(Long debateId) {
        Debate debate = requireDebate(debateId);
        debateMessageRepository.deleteByDebateId(debateId);
        debateRepository.delete(debate);
    }

    private Debate requireDebate(Long debateId) {
        return debateRepository.findById(debateId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Münazara bulunamadı."
                ));
    }
}
