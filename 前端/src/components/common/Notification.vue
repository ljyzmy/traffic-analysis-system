<template>
  <div 
    class="notification-container" 
    :class="[`bg-${typeClass}`]"
    v-show="visible"
  >
    <div class="container">
      <div class="notification-content">
        {{ message }}
        <button 
          type="button" 
          class="btn-close" 
          aria-label="Close"
          @click="closeNotification"
        ></button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, onMounted, computed } from 'vue';

export default {
  name: 'NotificationComponent',
  props: {
    type: {
      type: String,
      default: 'info',
      validator: (value) => ['info', 'success', 'warning', 'error'].includes(value)
    },
    message: {
      type: String,
      required: true
    },
    duration: {
      type: Number,
      default: 5000
    }
  },
  
  setup(props, { emit }) {
    const visible = ref(true);
    
    const typeClass = computed(() => {
      switch (props.type) {
        case 'success': return 'success';
        case 'warning': return 'warning';
        case 'error': return 'danger';
        default: return 'info';
      }
    });
    
    const closeNotification = () => {
      visible.value = false;
      emit('close');
    };
    
    onMounted(() => {
      if (props.duration > 0) {
        setTimeout(() => {
          closeNotification();
        }, props.duration);
      }
    });
    
    return {
      visible,
      typeClass,
      closeNotification
    };
  }
};
</script>

<style scoped>
.notification-container {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 1050;
  padding: 10px 0;
  color: white;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.2);
}

.notification-content {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.btn-close {
  background-color: transparent;
  color: white;
  filter: invert(1) grayscale(100%) brightness(200%);
}
</style> 