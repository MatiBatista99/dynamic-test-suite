package ar.cabal.dtos;


import java.util.ArrayList;
import java.util.List;

public class CaseGroup {

    private String groupName;
    private List<Case> cases = new ArrayList<>();

    public CaseGroup(String groupName) {
        this.groupName = groupName;
    }

    public void addCase(Case c) {
        cases.add(c);
    }

    public String getGroupName() {
        return groupName;
    }

    public List<Case> getCases() {
        return cases;
    }

}
