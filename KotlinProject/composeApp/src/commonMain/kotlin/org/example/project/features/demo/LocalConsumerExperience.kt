package org.example.project.features.demo

import androidx.compose.runtime.staticCompositionLocalOf
import org.example.project.core.data.ConsumerExperience
import org.example.project.core.data.RemoteConsumerExperience

val LocalConsumerExperience = staticCompositionLocalOf<ConsumerExperience> { RemoteConsumerExperience }
