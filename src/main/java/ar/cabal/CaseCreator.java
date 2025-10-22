package ar.cabal;

import ar.cabal.dtos.Case;

import java.util.List;

public interface CaseCreator {

    void createCases(CaseContext context, List<Case> cases);

}
