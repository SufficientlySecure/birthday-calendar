package org.birthdayadapter.util;

public class GroupListEntry {
    private final String title;
    private final int contactCount;
    private final int dateCount;

    public GroupListEntry(String title, int contactCount, int dateCount) {
        this.title = title;
        this.contactCount = contactCount;
        this.dateCount = dateCount;
    }

    public String getTitle() {
        return title;
    }

    public int getContactCount() {
        return contactCount;
    }

    public int getDateCount() {
        return dateCount;
    }
}
