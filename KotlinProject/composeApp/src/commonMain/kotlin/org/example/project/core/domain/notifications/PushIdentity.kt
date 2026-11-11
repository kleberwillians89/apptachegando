package org.example.project.core.domain.notifications

fun canRegisterPush(owner: String, currentOwner: String?, expectedEpoch: Long, currentEpoch: Long, existingOwner: String?, renewal: Boolean): Boolean =
    owner == currentOwner && expectedEpoch == currentEpoch && (!renewal || existingOwner == owner)

fun pushBindingMatches(owner: String?, version: String?, currentOwner: String?, payloadOwner: String, payloadVersion: String, confirmed: Boolean): Boolean =
    confirmed && owner != null && version != null && owner == currentOwner && owner == payloadOwner && version == payloadVersion

fun freshPushStamp(issued: Long, expires: Long, now: Long): Boolean =
    issued >= 0 && expires >= issued && now >= 0 && issued <= now + 5_000 && now - issued <= 30_000 && expires >= now && expires - issued <= 30_000
