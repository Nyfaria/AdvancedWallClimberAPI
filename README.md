# Advanced Wall Climber API (AWCAPI)

A powerful API for Minecraft mods that enables advanced wall climbing mechanics for entities. This API allows mod developers to easily add spider-like wall climbing behavior to any mob.

## Features

- **Full 3D Wall Climbing**: Entities can walk on walls, ceilings, and any surface
- **Smooth Orientation Transitions**: Natural-looking transitions when moving between surfaces
- **Custom Pathfinding**: Advanced pathfinding that understands 3D movement
- **Render Helpers**: Easy-to-use rendering utilities for proper model orientation
- **Cross-Platform**: Works on both Fabric and NeoForge

## Adding AWCAPI to Your Project

### Repository Setup

Add the GitHub Packages repository to your `build.gradle` or `settings.gradle`:

```groovy
repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/Nyfaria/AdvancedWallClimberAPI")
        credentials {
            // Use your GitHub username and a Personal Access Token with read:packages scope
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.key") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

### Authentication

GitHub Packages requires authentication even for public packages. Create a Personal Access Token (PAT) with `read:packages` scope:

1. Go to GitHub → Settings → Developer settings → Personal access tokens → Tokens (classic)
2. Generate a new token with `read:packages` scope
3. Add to your `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_PERSONAL_ACCESS_TOKEN
```

### Dependencies

#### Common Module (Multi-loader projects)
```groovy
dependencies {
    compileOnly "com.nyfaria.awcapi:awcapi-common:1.0.0+1.21.1"
}
```

#### Fabric
```groovy
dependencies {
    modImplementation "com.nyfaria.awcapi:awcapi-fabric:1.0.0+1.21.1"
}
```

#### NeoForge
```groovy
dependencies {
    implementation "com.nyfaria.awcapi:awcapi-neoforge:1.0.0+1.21.1"
}
```

## Quick Start Guide

### 1. Implement IAdvancedClimber

Make your entity implement `IAdvancedClimber`:

```java
public class MyClimbingMob extends PathfinderMob implements IAdvancedClimber {
    private final ClimberComponent climberComponent;

    public MyClimbingMob(EntityType<? extends MyClimbingMob> type, Level level) {
        super(type, level);
        this.climberComponent = new ClimberComponent(this);
        
        // Initialize climbing controllers
        this.moveControl = new ClimberMoveController<>(this);
        this.lookControl = new ClimberLookController<>(this);
        this.jumpControl = new ClimberJumpController<>(this);
    }

    @Override
    public ClimberComponent getClimberComponent() {
        return climberComponent;
    }

    @Override
    public Mob asMob() {
        return this;
    }

    @Override
    public float getMovementSpeed() {
        return (float) getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @Override
    public float getBlockSlipperiness(BlockPos pos) {
        return level().getBlockState(pos).getBlock().getFriction() * 0.91f;
    }

    @Override
    public boolean canClimbOnBlock(BlockState state, BlockPos pos) {
        return true; // Or add custom logic for non-climbable blocks
    }
}
```

### 2. Override Required Methods

Override these methods in your entity class:

```java
@Override
protected PathNavigation createNavigation(Level level) {
    ClimberPathNavigator<MyClimbingMob> navigator = new ClimberPathNavigator<>(this, level, false);
    navigator.setCanFloat(true);
    return navigator;
}

@Override
public void aiStep() {
    ClimberHelper.livingTickClimber(this);
    super.aiStep();
}

@Override
public void tick() {
    super.tick();
    ClimberHelper.tickClimber(this);
}

@Override
public void move(MoverType type, Vec3 movement) {
    ClimberHelper.handleMove(this, type, movement, true);
    super.move(type, movement);
    ClimberHelper.handleMove(this, type, movement, false);
}

@Override
public void travel(Vec3 travelVector) {
    if (!ClimberHelper.handleTravel(this, travelVector)) {
        super.travel(travelVector);
    }
    ClimberHelper.postTravel(this, travelVector);
}

@Override
public void jumpFromGround() {
    if (!ClimberHelper.handleJump(this)) {
        super.jumpFromGround();
    }
}

@Override
public BlockPos getOnPos() {
    return ClimberHelper.getAdjustedOnPosition(this, super.getOnPos());
}

@Override
public boolean onClimbable() {
    return false; // Disable vanilla climbing
}
```

### 3. Client-Side Rendering

For proper model orientation, use the render helpers:

```java
public static void onPreRenderLiving(LivingEntity entity, float partialTicks, PoseStack poseStack) {
    if (entity instanceof IAdvancedClimber climber) {
        Orientation orientation = climber.getOrientation();
        Orientation renderOrientation = climber.getClimberComponent().getRenderOrientation();
        
        if (renderOrientation != null) {
            poseStack.mulPose(renderOrientation.getRotation());
            
            // Apply attachment offset
            ClimberComponent component = climber.getClimberComponent();
            poseStack.translate(
                component.getAttachmentOffset(Direction.Axis.X, partialTicks),
                component.getAttachmentOffset(Direction.Axis.Y, partialTicks),
                component.getAttachmentOffset(Direction.Axis.Z, partialTicks)
            );
        }
    }
}
```

### 4. Using with Mixins (for vanilla entities)

If you want to add climbing to vanilla entities like Spider:

```java
@Mixin(Spider.class)
public abstract class SpiderMixin extends Monster implements IAdvancedClimber {
    @Unique
    private ClimberComponent climberComponent;

    protected SpiderMixin(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(EntityType<?> type, Level level, CallbackInfo ci) {
        this.climberComponent = new ClimberComponent(this);
        this.moveControl = new ClimberMoveController<>(this);
        this.lookControl = new ClimberLookController<>(this);
        this.jumpControl = new ClimberJumpController<>(this);
    }

    @Override
    public ClimberComponent getClimberComponent() {
        return climberComponent;
    }

    // ... implement other IAdvancedClimber methods
}
```

## API Reference

### IAdvancedClimber Interface

The main interface your entity must implement:

| Method | Description |
|--------|-------------|
| `getClimberComponent()` | Returns the ClimberComponent instance |
| `asMob()` | Returns the entity as a Mob |
| `getMovementSpeed()` | Returns the entity's movement speed |
| `getBlockSlipperiness(BlockPos)` | Returns slipperiness for a block position |
| `canClimbOnBlock(BlockState, BlockPos)` | Whether the entity can climb on a specific block |
| `getOrientation()` | Gets the current orientation |
| `getVerticalOffset(float)` | Gets the vertical offset for rendering |

### ClimberHelper Static Methods

| Method | Description |
|--------|-------------|
| `tickClimber(IAdvancedClimber)` | Call in entity's tick() |
| `livingTickClimber(IAdvancedClimber)` | Call in entity's aiStep() |
| `handleTravel(IAdvancedClimber, Vec3)` | Call in travel(), returns true if handled |
| `postTravel(IAdvancedClimber, Vec3)` | Call after travel() |
| `handleMove(IAdvancedClimber, MoverType, Vec3, boolean)` | Call before/after move() |
| `handleJump(IAdvancedClimber)` | Call in jumpFromGround(), returns true if handled |
| `getAdjustedOnPosition(IAdvancedClimber, BlockPos)` | Get adjusted ground position |

### Movement Controllers

- `ClimberMoveController<T>` - Handles 3D movement
- `ClimberLookController<T>` - Handles looking in local space
- `ClimberJumpController<T>` - Handles jumping from any surface
- `ClimberPathNavigator<T>` - Pathfinding that understands wall climbing

## Building from Source

```bash
git clone https://github.com/Nyfaria/AdvancedWallClimberAPI.git
cd AdvancedWallClimberAPI
./gradlew build
```

## Publishing (For Maintainers)

The project automatically publishes to GitHub Packages when:
- A new release is created on GitHub
- The publish workflow is manually triggered

To publish manually:
```bash
./gradlew publish
```

## License

All Rights Reserved - Contact Nyfaria for licensing inquiries.

## Credits

- **Nyfaria** - Original author and maintainer
- Based on concepts from Nyf's Spiders mod

## Support

For issues and feature requests, please use the [GitHub Issues](https://github.com/Nyfaria/AdvancedWallClimberAPI/issues) page.

